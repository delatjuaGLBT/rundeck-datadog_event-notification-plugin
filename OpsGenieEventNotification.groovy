import com.dtolabs.rundeck.plugins.notification.NotificationPlugin;
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants;
import com.dtolabs.rundeck.core.plugins.configuration.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode

// See https://www.opsgenie.com/docs/web-api/alert-api#createAlertRequest 

// curl -XPOST 'https://api.opsgenie.com/v1/json/alert' -d '
// {
//     "apiKey": "eb243592-faa2-4ba2-a551q-1afdf565c889",
//     "message" : "WebServer3 is down",
//     "teams" : ["operations", "developers"]
// }'
// 'https://api.opsgenie.com/v1/json/alert?apiKey=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'

class DEFAULTS {
    static String OPSGENIE_EVENT_URL = "https://api.opsgenie.com/v1/json/alert?apiKey="
    static String SUBJECT_LINE='Rundeck JOB: ${job.status} [${job.project}] \"${job.name}\" run by ${job.user} (#${job.execid})'
    static String API_KEY='e9288006-bda8-4b5d-9b84-860857fe9091'
}

/**
 * Expands the Subject string using a predefined set of tokens
 */
def titleString(text,binding) {
  //defines the set of tokens usable in the subject configuration property
  def tokens=[
    '${job.status}': binding.execution.status.toUpperCase(),
    '${job.project}': binding.execution.job.project,
    '${job.name}': binding.execution.job.name,
    '${job.group}': binding.execution.job.group,
    '${job.user}': binding.execution.user,
    '${job.execid}': binding.execution.id.toString()
  ]
  text.replaceAll(/(\$\{\S+?\})/){
    tokens[it[0]]
  }
}

/**
 * Setting Alert Info
**/
def alertInfo(binding) {
  //System.err.println("DEBUG: bindingData="+binding.execution.status)
  switch (binding.execution.status) {
    case "succeeded" :
      alert_info = "info"
      break
    case "failed" :
      alert_info = "error"
      break
    default:
      alert_info = "info"
      break
  }
   return alert_info
}

/**
 * @param execution
 * @param configuration
 */
def triggerEvent(Map execution, Map configuration) {
  //System.err.println("DEBUG: api_key="+configuration.api_key)
  //System.err.println("DEBUG: excutionData="+execution)
  def expandedTitle = titleString(configuration.subject, [execution:execution])
  def expandedAlertinfo = alertInfo([execution:execution])
  def job_data = [
    title: expandedTitle,
    text: "Please see: " + execution.href,
    tags: "rundeck:" + execution.job.name,
    alert_type: expandedAlertinfo
  ]

  // Send the request.
  def url = new URL(DEFAULTS.OPSGENIE_EVENT_URL+configuration.api_key)
  def connection = url.openConnection()
  connection.setRequestMethod("POST")
  connection.addRequestProperty("Content-type", "application/json")
  connection.doOutput = true
  def writer = new OutputStreamWriter(connection.outputStream)
  def json = new ObjectMapper()
  writer.write(json.writeValueAsString(job_data))
  writer.flush()
  writer.close()
  connection.connect()

  // process the response.
  def response = connection.content.text
  //System.err.println("DEBUG: response: "+response)
  JsonNode jsnode= json.readTree(response)
  def status = jsnode.get("status").asText()
  if (! "success".equals(status)) {
      System.err.println("ERROR: OpsGenieEventNotification plugin status: " + status)
  }
}

/**
 * Main
**/
rundeckPlugin(NotificationPlugin){
    title="OpsGenie_Event"
    description="Create a Trigger event."
    configuration{
        subject title:"Subject", description:"Incident subject line. Can contain \${job.status}, \${job.project}, \${job.name}, \${job.group}, \${job.user}, \${job.execid}", defaultValue:DEFAULTS.SUBJECT_LINE, required:true
        api_key title:"API Key", description:"OpsGenie API key", defaultValue:DEFAULTS.API_KEY, required:true
    }
    onstart { Map execution, Map configuration ->
        triggerEvent(execution, configuration)
        true
    }
    onfailure { Map execution, Map configuration ->
        triggerEvent(execution, configuration)
        true
    }
    onsuccess { Map execution, Map configuration ->
        triggerEvent(execution, configuration)
        true
    }

}
