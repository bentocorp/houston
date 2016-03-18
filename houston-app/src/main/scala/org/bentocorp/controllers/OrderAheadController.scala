package org.bentocorp.controllers

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util
import java.util.{Calendar, TimeZone}
import javax.annotation.PostConstruct

import org.apache.commons.lang.exception.ExceptionUtils
import org.bentocorp.BentoBox.Item.Temp
import org.bentocorp._
import org.bentocorp.api.APIResponse._
import org.bentocorp.api.ws.OrderAction
import org.bentocorp.db.OrderDao
import org.bentocorp.dispatch.{Driver => BentoDriver, DriverManager, OrderManager}
import org.bentocorp.houston.config.BentoConfig
import org.bentocorp.houston.dispatch.routific._
import org.bentocorp.houston.util.{HttpUtils, TimeUtils}
import org.bentocorp.redis.Redis
import org.redisson.client.RedisConnection
import org.redisson.client.codec.StringCodec
import org.redisson.client.protocol.RedisCommands
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation._
import org.springframework.web.context.request.{RequestAttributes, RequestContextHolder}
import org.springframework.web.servlet.ModelAndView

import scala.collection.JavaConversions._
import scala.collection.mutable.{Map => MMap, Set => MSet, ListBuffer}

@Controller
@RequestMapping(Array("/api/routific"))
class OrderAheadController {

    val logger = LoggerFactory.getLogger(classOf[OrderAheadController])

    @Autowired
    var config: BentoConfig = _

    @Autowired
    var orderManager: OrderManager = _

    @Autowired
    var driverManager: DriverManager = _

    @Autowired
    var redis: Redis = _

    @Autowired
    var phpService: PhpService = _

    @Autowired
    var orderDao: OrderDao = _

    var token: String = _

    @PostConstruct
    def init() {
        token = config.getString("routific.token")
    }

    // "routific-job_ij90yz608"
    private def _redisKeyPendingJob(jobId: String) = s"routific-job_$jobId"

    // "routific-schedule_01/26/2016-0"
    private def _redisKeySchedule(dateStr: String, shiftValue: Int) = s"routific-schedule_$dateStr-$shiftValue"

    @ResponseBody
    @RequestMapping(Array("/route"))
    def route(@RequestParam("date") dateStr: String,
              @RequestParam("shift") shiftValue: Int): String = {
        var redisConnection: RedisConnection = null
        try {
            // Order#id -> Visit
            val visits = new util.HashMap[String, Visit]()
            // We only want to route unassigned orders!
            _getOrderAheadOrders(dateStr, shiftValue).filter(_._2.getStatus == Order.Status.UNASSIGNED) foreach {
                case (_, o) =>
                    // Use order key instead of id because Routific does not allow hyphenated keys
                    visits.put(o.getOrderKey.toString, new Visit(o))
            }

            if (visits.size <= 0) {
                throw new Exception("Error - No orders to route!")
            }

            val shift = Shift.values()(shiftValue)

            val options = new Options
            val available: List[BentoDriver] = driverManager.drivers.toMap.values.toList
            val fleet: List[BentoDriver] = available filter (_.shiftType == Shift.Type.ORDER_AHEAD)
            val drivers0 =
                if (fleet.isEmpty) {
                    options.`with`("min_vehicles", true)
                    available
                } else {
                    fleet
                }
            // Int -> Driver
            val fleet0 = new util.HashMap[String, Driver]()
            var limit = 6
            drivers0 foreach { d =>
                if (limit > 0) {
                    fleet0.put(d.id + "", new Driver(shift))
                    limit = limit - 1
                }
            }

            val input = new Input(visits, fleet0, options)

            // Submit the job to Routific for processing
            val (code, res) = HttpUtils.postStr(Routific.LONG_VRP_URL, Map("Authorization" -> token,
                "Content-Type" -> "application/json"), ScalaJson.stringify(input))
            if (code != 202) {
                throw new Exception(code + " - " + res)
            }
            val job = ScalaJson.parse[Job](res, classOf[Job])
            // Manually populate dateStr and shift so we know which work period this job was started for
            job.date = dateStr
            job.shift = shift

            // Persist to database #6 (8-9 are flushed regularly!)
            redisConnection = redis.connect(6)
            val redisKey = _redisKeyPendingJob(job.jobId) // routific-job_678abc
            redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SET, redisKey, ScalaJson.stringify(job))
            success(job.jobId)
        } catch {
            case e: Exception =>
                logger.error(e.getMessage, e)
                error(1, e.getMessage)
        } finally {
            if (redisConnection != null) redisConnection.closeAsync()
        }
    }

    // Check to see if a Routific job has finished and if yes, associate the work period with the output and persist in
    // Redis
    @ResponseBody
    @RequestMapping(Array("/checkAndPersist"))
    def checkAndPersist(@RequestParam(value = "jobId") jobId: String): String = {
        var redisConnection: RedisConnection = null
        try {
            // Make a GET request to see if the job has finished
            var res0: String = HttpUtils.get(s"https://api.routific.com/jobs/${jobId}", Map.empty)
            val job = ScalaJson.parse[Job](res0, classOf[Job])

            if (job.status == Job.Status.FINISHED) {
                // Fetch job meta-data from Redis
                redisConnection = redis.connect(6)
                res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.GET, _redisKeyPendingJob(jobId))
                if (res0 == null) {
                    throw new Exception("Error - There is no pending job data for " + jobId + " so the work period is unknown")
                }
                val j_ = ScalaJson.parse(res0, classOf[Job])
                job.date = j_.date
                job.shift = j_.shift
                job.time = new SimpleDateFormat("dd MMM yyyy HH:mm:ss").format(System.currentTimeMillis()).toUpperCase

                job.minVehicles = job.output.solution count {
                    case (_, visits) => visits.size > 1
                }

                val redisKey = _redisKeySchedule(j_.date, j_.shift.ordinal())

                logger.debug("OrderAheadController.checkAndPersist - SET " + redisKey)
                res0 = redisConnection.sync(
                    StringCodec.INSTANCE, RedisCommands.SET, redisKey, ScalaJson.stringify(job))
            }
            success(job)
        } catch {
            case e: Exception =>
                logger.error(e.getMessage, e)
                error(1, e.getMessage)
        } finally {
            if (redisConnection != null) {
                redisConnection.closeAsync()
            }
        }
    }

    // This format is used in Atlas because it is more human-readable, as opposed to the more standard and
    // code-friendly "yyyy-MM-dd" format used elsewhere
    final val LOCAL_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    private def _getOrderAheadOrders(dateStr: String, shiftValue: Int): MMap[Long, Order[Bento]] =
    {
        println(dateStr + "-" + shiftValue)
        val ld: LocalDate = LocalDate.parse(dateStr, LOCAL_DATE_FORMAT)

        val shift = Shift.values()(shiftValue)

        // Since all dispatchers are currently in California, assume PST
        val start = new Timestamp(TimeUtils.getMillis(ld, shift.start, TimeZone.getTimeZone("PST")))
        val end = new Timestamp(TimeUtils.getMillis(ld, shift.end, TimeZone.getTimeZone("PST")))

        //var rows = orderDao.getOrderAddons(start, end);
        //println(ScalaJson.stringify(rows))

        val f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        logger.debug(
            s"Getting OA orders >= ${f.format(start)} (${start.getTime}) and < ${f.format(end)} (${end.getTime})")
        // OrderManager#createOAOrders returns a map because
        val orders = orderManager.createOrderAheadOrders(start, end)
        logger.debug("Got " + orders.size)

        orders
    }

    @RequestMapping(Array("/getOrderAheadOrders"))
    @ResponseBody
    def getOrderAheadOrders(@RequestParam("date") date: String,
                            @RequestParam("shift") shift: Int): String = {
        println("Here-" + date + ", " + shift)
        try {
            // Needs to a Java List to be serialized properly because success() is written in Java
            val orders = new util.ArrayList[Order[Bento]]
            _getOrderAheadOrders(date, shift) foreach {
                case (key, o) => orders.add(o)
            }
            success(orders)
        } catch {
            case e: Exception =>
                logger.error(e.getMessage, e)
                error(1, e.getMessage)
        }
    }

    // Fetch most recent job data from Redis given a date and a shift
    private def _getMostRecentJob(dateStr: String, shiftValue: Int): Option[Job] = {
        var redisConnection: RedisConnection = null
        try {
            redisConnection = redis.connect(6)
            val redisKey = _redisKeySchedule(dateStr, shiftValue) // "routific-schedule_01/26/2016-0"
            logger.debug("GET " + redisKey)
            val res0: String = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.GET, redisKey)

            if (res0 != null) {
                Some(ScalaJson.parse(res0, classOf[Job]))
            } else {
                None
            }
        } finally {
            if (redisConnection != null) redisConnection.closeAsync()
        }
    }

    // Use String (instead of Int) for shiftValue so we can represent a default request by setting it to null
    @RequestMapping(Array("/getMostRecentJob"))
    @ResponseBody
    def getMostRecentJob(@RequestParam(value = "date", defaultValue = "") dateStr: String,
                         @RequestParam(value = "shift", defaultValue = "") shiftValue: String,
                         @RequestParam(value = "zone", defaultValue = "PST") zone: String): String = {
        try {
            // For now, default to PST (but this will need to be sent by the client when we open in multiple time zones
            val calendar = Calendar.getInstance(TimeZone.getTimeZone(zone))

            val dateStr0 =
                if (dateStr.isEmpty) {
                    // If not supplied, use current date
                    val ld = LocalDate.of(
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH) + 1, // java.util.Calendar uses 0-based months
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                    ld.format(LOCAL_DATE_FORMAT)
                } else {
                    dateStr
                }

            val shiftValue0 =
                if (shiftValue.isEmpty) {
                    Shift.getShiftEqualToOrGreaterThan(calendar.getTimeInMillis).ordinal()
                } else {
                    shiftValue.toInt
                }

            val job = _getMostRecentJob(dateStr0, shiftValue0) match {
                case Some(v) => v
                case _ => {
                    val j = new Job
                    j.date = dateStr0
                    j.shift = Shift.values()(shiftValue0)
                    j
                }
            }

            success(job)
        } catch {
            case e: Exception =>
                logger.error(e.getMessage, e)
                error(1, e.getMessage)
        }
    }

    @RequestMapping(Array("/assign"))
    @ResponseBody
    def assign(@RequestParam(value = "date") dateStr: String,
               @RequestParam(value = "shift") shiftValue: Int): String = {
        try {
            // First we have to check to see if there's any routing data available for the user-supplied
            // work period (date & shift)
            val job = _getMostRecentJob(dateStr, shiftValue) match {
                case Some(v) =>
                    v
                case _ =>
                    throw new Exception(s"No Routific data available for $dateStr-$shiftValue")
            }
            // Then check to see if this is a successful job (that all orders can be fulfilled)
            val unserved = job.output.numUnservedVisits
            if (unserved > 0) {
                throw new Exception(s"The latest job (${job.jobId}) for $dateStr-$shiftValue has $unserved unserved orders")
            }

            // Lastly, make sure today
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("PST"))
            val cLocalDate = LocalDate.of(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1, // java.util.Calendar uses 0-based months
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            val cShift = Shift.getShiftEqualToOrGreaterThan(System.currentTimeMillis())
            if (cLocalDate.format(LOCAL_DATE_FORMAT) != dateStr || cShift.ordinal() != shiftValue) {
                throw new Exception("Sorry can only assign to this work period")
            }

            // Even if the job is successful, we have to make sure that it is up-to-date. Fetch all Order Ahead orders
            // from the database and see if they are all included in the job.
            val servedOrders = job.output.solution.values.foldLeft(MSet.empty[Long])((a, b) => {
                // 0-th element is always the starting spot
                (1 until b.size) foreach { i =>
                    val visit = b(i)
                    // For additional safety, make sure the orders are all Order Ahead orders
                    if (!orderManager.getOrder("o-" + visit.id).isOrderAhead) {
                        throw new Exception(s"Routed order ${visit.id} is not Order Ahead")
                    }
                    a += visit.id.toLong
                }
                a
            })
            println(s"SERVED=[${servedOrders.mkString(",")}")
            // _getOrderAheadOrders() retrieves all orders so we must filter out the ones that have already been assigned
            val orderIds: List[Long] = _getOrderAheadOrders(dateStr, shiftValue).filter(_._2.getStatus == Order.Status.UNASSIGNED).keys.toList
            if (servedOrders.size != orderIds.size) {
                throw new Exception(s"The latest job (${job.jobId}) size is wrong. Please try routing again.")
            }
            orderIds foreach { id =>
                if (!servedOrders.contains(id)) {
                    throw new Exception(s"The latest job (${job.jobId}) does not fullfill o-$id. It may be outdated so try routing again.")
                }
            }
            // Now check the drivers
            val scheduled: Set[String] = driverManager.drivers.toMap.values
                    .filter(_.shiftType == Shift.Type.ORDER_AHEAD)
                    .map(_.id.toString)
                    .toSet
            val fleet = job.output.solution.keys.toSet
            if (scheduled != fleet) {
                throw new Exception(s"Fleet does not reflect current drivers scheduled for Order Ahead")
            }

            logger.debug(">>>>>\n" + ScalaJson.stringify(job))
            // If we make it here, all is well so we can go ahead and assign (finally!)
            job.output.solution foreach {
                case (driverId, servedVisits) =>
                    // Again, ignore the first visit because it is always the starting point
                    (1 until servedVisits.size) foreach { i =>
                        val v = servedVisits(i)
                        val pToken: String = RequestContextHolder.currentRequestAttributes().getAttribute("token", RequestAttributes.SCOPE_REQUEST).asInstanceOf[String]
                        //println("PHP_TOKEN=" + pToken)
                        println("o-" + v.id + "," + driverId.toLong + "," + "-1" + "," + pToken)

                        logger.debug(s"ASSIGN ${driverId}, o-${v.id}")
                        val modifiedOrder = orderManager.assign("o-" + v.id, driverId.toLong, null, pToken)

                        val p = OrderAction.make(OrderAction.Type.ASSIGN, modifiedOrder, driverId.toLong, null).from("houston").toGroup("atlas")

                        val params = Map("rid" -> p.rid, "from" -> p.from, "to" -> p.to, "subject" -> p.subject,
                            "body" -> ScalaJson.stringify(p.body), "token" -> pToken)
                        val (_, str) = HttpUtils.postForm(
                            config.getString("node.url") + "/api/push",
                            Map("Content-Type" -> "application/json"),
                            params
                        )
                        //ScalaJson.parse(str, new TypeReference[APIResponse[String]]() { })

                        // Send push notification to driver
                        val p2 = OrderAction.make(OrderAction.Type.ASSIGN, modifiedOrder, driverId.toLong, null).from("houston").toRecipient("d-" + driverId)
                        val params2 = Map("rid" -> p2.rid, "from" -> p2.from, "to" -> p2.to, "subject" -> p2.subject,
                            "body" -> ScalaJson.stringify(p2.body), "token" -> pToken)
                        val (_, str2) = HttpUtils.postForm(
                            config.getString("node.url") + "/api/push",
                            Map("Content-Type" -> "application/json"),
                            params2
                        )
                    }
            }
            success(0, "OK")
        } catch {
            case e: Exception => // Catch all Exceptions
                // Log the error
                logger.error("Error in OrderAheadController.assign() - " + e.getMessage, e)
                // Forward the error to the front-end (Atlas)
                error(1, e.getMessage)
        } finally {
        }
    }

    @RequestMapping(Array("/report"))
    def report(@RequestParam("date") dateStr: String,
               @RequestParam("shift") shiftValue: Int): ModelAndView = {

        val model = new util.HashMap[String, Any]()
        try {
            val orders = _getOrderAheadOrders(dateStr, shiftValue)
            val job = _getMostRecentJob(dateStr, shiftValue) match {
                case Some(v) =>
                    v
                case _ =>
                    val j = new Job
                    j.date = dateStr
                    j.shift = Shift.values()(shiftValue)
                    j
            }
            model.put("job", job)
            if (job.output != null && job.output.numUnservedVisits == 0) {

                val dishes = new util.HashMap[String, util.HashMap[BentoBox.Item, Int]]()

                val addons = new util.HashMap[String, util.HashMap[AddOnList.AddOn, Int]]()

                val inventories = new util.HashMap[String, DriverInventory]()

                var count = 0
                job.output.solution foreach {
                    case (driverId, visits) =>
                        //
                        val driver = driverManager.getDriver(driverId.toLong)
                        inventories.put(driverId, new DriverInventory(driver.name))

                        val dishes0 = new util.HashMap[BentoBox.Item, Int]()
                        val addons0 = new util.HashMap[AddOnList.AddOn, Int]()
                        // For each visit (order)
                        (1 until visits.size) foreach { i =>
                            count = count + 1
                            val v = visits(i)
                            // Retrieve the order from cache
                            val order = orders(v.id.toLong) // Throws Exception if order doesn't exist
                            order.item foreach {
                                case wrapper: BentoBox =>
                                    wrapper.items foreach { dish =>
                                        if (!dishes0.contains(dish)) {
                                            dishes0.put(dish, 0)
                                        }
                                        // Increment counter
                                        dishes0.put(dish, dishes0.get(dish) + 1)
                                    }
                                case wrapper: AddOnList =>
                                    wrapper.items foreach { addon =>
                                        if (!addons0.contains(addon)) {
                                            addons0.put(addon, 0)
                                        }
                                        addons0.put(addon, addons0.get(addon) + addon.qty)
                                    }
                            }
                        }
                        dishes.put(driverId, dishes0)
                        addons.put(driverId, addons0)
                }

                dishes foreach {
                    case (driverId, box) =>
                        //            if (!inventories.contains(driverId)) {
                        //              inventories.put(driverId, new DriverInventory)
                        //            }
                        val inventory = inventories.get(driverId)
                        box foreach {
                            case (item, count) =>
                                item.temp match {
                                    case Temp.HOT => inventory.hots.add((s"${item.name} (${item.label})", count))
                                    case Temp.COLD => inventory.colds.add((s"${item.name} (${item.label})", count))
                                }
                        }
                }
                addons foreach {
                    case (driverId, list) =>
                        //            if (!inventories.contains(driverId)) {
                        //              inventories.put(driverId, new DriverInventory)
                        //            }
                        val inventory = inventories.get(driverId)
                        list foreach {
                            case (addon, count) => inventory.addons.add((s"${addon.name}", count))
                        }
                }

                val s = statistics(dateStr, shiftValue)
                s foreach {
                    case (driverId, stats) =>
                        val d = inventories.get(driverId)
                        d.start = stats._1;
                        d.end = stats._2;
                        d.minutes = stats._3;
                }
                model.put("count", count)
                model.put("inventories", inventories)
            }
        } catch {
            case e: Exception => model.put("error", ExceptionUtils.getStackTrace(e))
        }
        logger.debug(ScalaJson.stringify(model));
        new ModelAndView("report", model)
    }

    //  @RequestMapping(Array("/statistics"))
    //  @ResponseBody
    def statistics(@RequestParam("date") dateStr: String,
                   @RequestParam("shift") shiftValue: Int) = {
        //    try {
        val job = _getMostRecentJob(dateStr, shiftValue) match {
            case Some(v) => v
            case _ => throw new Exception("No data")
        }

        val s = MMap[String, (String, String, String)]()

        val LD = LocalDate.now
        var numberOfVisits = 0
        job.output.solution foreach {
            case (driverId, visits) =>
                val arrivalTime = visits(0).arrivalTime
                var sum = 0L
                var prev = TimeUtils.getMillis(LD, visits(0).arrivalTime, TimeZone.getTimeZone("PST"))
                (1 until visits.size) foreach { i =>
                    val curr = TimeUtils.getMillis(LD, visits(i).arrivalTime, TimeZone.getTimeZone("PST"))
                    sum = sum + ((curr - prev) / 60000) // minutes
                    prev = curr
                    numberOfVisits = numberOfVisits + 1
                }
                s += driverId ->(arrivalTime.toString, visits.last.arrivalTime.toString, sum.toString)
        }
        //      val buffer = new StringBuffer("COUNT=%s</br>" format numberOfVisits)
        //      s foreach {
        //        case (key, value) => buffer.append(s"%2s -> ${value._1}, ${value._2}, ${value._3.toInt/60.0}</br>" format key)
        //      }
        //      buffer.toString
        s
        //    } catch {
        //      case e: Exception => error(1, e.getMessage)
        //    }
    }

    /* Model classes for Freemarker */
    class DriverInventory(val name: String) {
        val hots = new util.ArrayList[(String, Int)]()
        val colds = new util.ArrayList[(String, Int)]()
        val addons = new util.ArrayList[(String, Int)]()

        var start: String = _
        var end: String = _
        var minutes: String = _
    }

}
