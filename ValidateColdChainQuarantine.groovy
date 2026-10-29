import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.XmlSlurper

def Message processData(Message message) {
    def body = message.getBody(java.lang.String) as String
    def messageLog = messageLogFactory.getMessageLog(message)
    
    // GxP Cold-Chain Acceptance Limits (+2.0°C to +8.0°C)
    double minAllowedTemp = 2.0
    double maxAllowedTemp = 8.0
    double recordedTemp = 4.5
    boolean excursionBreached = false
    
    try {
        if (body != null && body.trim().startsWith("<")) {
            def desadv = new XmlSlurper().parseText(body)
            def meaValue = desadv.'**'.find { it.name() == 'MEA' }?.C_C084?.D_6000?.text()
            if (meaValue && meaValue.isNumber()) {
                recordedTemp = meaValue.toDouble()
            }
        }
    } catch (Exception e) {
        if (messageLog != null) {
            messageLog.addCustomHeaderProperty("GxP_Warning", "Payload XML parse exception: " + e.getMessage())
        }
    }
    
    // GxP Validation Logic
    if (recordedTemp < minAllowedTemp || recordedTemp > maxAllowedTemp) {
        excursionBreached = true
        message.setProperty("ColdChain_Status", "EXCURSION_DETECTED")
        message.setProperty("Batch_Stock_Status", "QUARANTINE")
        if (messageLog != null) {
            messageLog.addCustomHeaderProperty("GxP_ColdChain_Alert", "BREACH: " + recordedTemp + " C -> Placed in Quarantine")
        }
    } else {
        message.setProperty("ColdChain_Status", "COMPLIANT")
        message.setProperty("Batch_Stock_Status", "UNRESTRICTED")
        if (messageLog != null) {
            messageLog.addCustomHeaderProperty("GxP_ColdChain", "PASS: " + recordedTemp + " C (Compliant)")
        }
    }
    
    message.setProperty("GxP_Recorded_Temperature", String.valueOf(recordedTemp))
    return message
}