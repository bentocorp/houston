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
                    <span style="color: green">Status: SUCCESS</span><br/>
                    COUNT=${count}
                    <table cellspacing="0" cellpadding="5px">
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
                                <td style="vertical-align: top;">
                                    <table cellspacing="0" style="border: none;">
                                    <#list inventories[driverId].colds() as cold>
                                        <tr style="border: none;">
                                            <td style="border: none;">${cold._1()}</td>
                                            <td style="border: none; vertical-align: top">${cold._2()}</td>
                                        </tr>
                                    </#list>
                                    </table>
                                </td>
                                <td style="vertical-align: top">
                                    <table cellspacing="0" style="border: none;">
                                    <#list inventories[driverId].hots() as hot>
                                        <tr style="border: none;">
                                            <td style="border: none;">${hot._1()}</td>
                                            <td style="border: none; vertical-align: top;">${hot._2()}</td>
                                        </tr>
                                    </#list>
                                    </table>
                                </td>
                                <td style="vertical-align: top">
                                    <table cellspacing="0" style="border: none;">
                                    <#list inventories[driverId].addons() as addon>
                                        <tr style="border: none;">
                                            <td style="border: none;">${addon._1()}</td>
                                            <td style="border: none; vertical-align: top">${addon._2()}</td>
                                        </tr>
                                    </#list>
                                    </table>
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