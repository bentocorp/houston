<!DOCTYPE HTML>
<html>
<head>
    <title>Report</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <style>
        table, th, td { border: 1px solid black }
    </style>
</head>
<body>
    <#if error??>
        <div style="color: red;">${error}</div>
    <#else>
        <#if !(job.jobId??)>
            <div>
                <span style="font-size: 14pt">No data available for ${job.date}, ${job.getShiftString()}</span><br/>
                Please submit a job then generate the report again.
            </div>
        <#else>
            <div>
                <span style="font-size: 14pt">Report for ${job.date}, ${job.getShiftString()}</span><br/>
                <#if job.output.numUnservedVisits gt 0>
                    <span style="color: red">Status: FAILURE</span><br/>
                    <table>
                    <#list job.output.unserved?keys as prop>
                        <tr>
                            <td>${prop}</td><td>${job.output.unserved[prop]}</td>
                        </tr>
                    </#list>
                    </table>
                <#else>
                    <span style="color: green">Status: SUCCESS</span>
                    <table>
                        <tr>
                            <th>Driver</th>
                            <th>Name</th>
                            <th>Cold</th>
                            <th>Hot</th>
                            <th>Addons</th>
                            <th>Start</th>
                            <th>End</th>
                            <th>Minutes</th>
                        </tr>
                        <#list inventories?keys as driverId>
                            <tr>
                                <td style="vertical-align: top">${driverId}</td>
                                <td style="vertical-align: top">
                                    ${inventories[driverId].name()}</td>
                                <td style="vertical-align: top">
                                    <#list inventories[driverId].colds() as cold>
                                        ${cold._1()} x${cold._2()}</br>
                                    </#list>
                                </td>
                                <td style="vertical-align: top">
                                    <#list inventories[driverId].hots() as hot>
                                        ${hot._1()} x${hot._2()}</br>
                                    </#list>
                                </td>
                                <td style="vertical-align: top">
                                    <#list inventories[driverId].addons() as addon>
                                        ${addon._1()} x${addon._2()}</br>
                                    </#list>
                                </td>
                                <td style="vertical-align: top">
                                    ${inventories[driverId].start()}
                                </td>
                                <td style="vertical-align: top">
                                    ${inventories[driverId].end()}
                                </td>
                                <td style="vertical-align: top">
                                    ${inventories[driverId].minutes()}
                                </td>
                            </tr>
                        </#list>
                    </table>
                </#if>
            </div>
        </#if>
    </#if>
</body>
</html>