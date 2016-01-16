@Grab(group='org.eclipse.jetty.aggregate', module='jetty-all', version='7.6.15.v20140411')
import org.eclipse.jetty.server.*
import org.eclipse.jetty.servlet.*
import groovy.json.JsonSlurper
import javax.servlet.http.*
import javax.servlet.*


class SimpleGroovyServlet extends HttpServlet {
	final String KEY="AIzaSyCh8IK9eDqqGB8Wx2k0Vr_pcisZD1qw74A"
	HashMap trackedIDs=new HashMap();
	long updateCount = 0;
	long lastPrintCount = 0;
	void doGet(HttpServletRequest req, HttpServletResponse resp) {
		// println "GET  "+req.getRequestURL()+"   query string:"+req.getQueryString();
		def uuids = req.getParameterMap().get("uuid")
		
		if(req.getPathInfo().equals("/tracknewpackage")) {
			def responseString = "{ \"ackUUID\":\""+uuids+"\" }"
			double lat=Double.parseDouble(req.getParameterMap().get("destinationLat")[0])
			double lon=Double.parseDouble(req.getParameterMap().get("destinationLon")[0])
			trackedIDs.putAt(uuids[0],new TrackablePackage(uuids[0], new Coordinate(lat,lon)));
			resp.setContentType("application/json");
			def writer = resp.getWriter();
			writer.print(responseString);
			writer.flush();
			println "\t\t  "+responseString;
		}		
		if(req.getPathInfo().equals("/trackPackages")){
			def packageInfos=new ArrayList()
			for(String id:uuids){
				if(trackedIDs.containsKey(id)){
					packageInfos.add((trackedIDs.get(id,null)))
				}
			}
			def writer = resp.getWriter();
			resp.setContentType("text/html")
			Scanner scanner;
			for(int x=0;x<packageInfos.size();x++){
				//scanner = new Scanner( new File("HTML/GoogleMapsFront.HTML") );
				//String front = scanner.useDelimiter("\\A").next();
				//scanner = new Scanner( new File("HTML/GoogleMapsRear.HTML") );
				//String rear = scanner.useDelimiter("\\A").next();
				String front="<iframe width=\"600\" height=\"450\" frameborder=\"0\" style=\"border:0\" src=\"https://www.google.com/maps/embed/v1/place?q=";
				String rear="&amp;key=AIzaSyCh8IK9eDqqGB8Wx2k0Vr_pcisZD1qw74A\" allowfullscreen=\"\"></iframe>"
				Coordinate c=packageInfos.get(x).getLocation()
				println c.lat
				writer.print(front+c.lat+"%20"+c.lon+rear);
			}
			scanner = new Scanner( new File("HTML/TrackNewPackageForm.HTML") );
			String text = scanner.useDelimiter("\\A").next();
			scanner.close()
			writer.print(text);
			writer.flush();
		}

	}


	void doPost(HttpServletRequest req, HttpServletResponse resp) {
		//        println "POST: "+req.getRequestURL();
		if(req.getPathInfo().startsWith("/packagetrackupdate/")) {

			try {
				BufferedReader reader = req.getReader();
				String line = null;
				while ((line = reader.readLine()) != null) {
				
					def slurper=new JsonSlurper()
					def inf=slurper.parseText(line);
					def uuid = req.getPathInfo().replace("/packagetrackupdate/","");
					def currentPackage = trackedIDs.get(uuid);
					if(line.contains("delivered")) {
						//This code registers delivery events
						println uuid +" -> "+ line;
						currentPackage.setDelivered(true);
					}
					else{
						//This code tracks all non delivery events
						currentPackage.setLocation(new Coordinate(Double.parseDouble(inf.lat),Double.parseDouble(inf.lon)))
						//println req.getPathInfo()+" -> "+line; //Comment out if you only want to print the delivered updates
					}
				}

				updateCount++;
				if((updateCount - lastPrintCount) >= 1000) {
					println "packagetrackupdate count: "+updateCount;
					lastPrintCount = updateCount;
				}

			} catch (Exception e) { e.printStackTrace(); /*report an error*/ }
		}
	}

}

def server = new Server(8000);
ServletHandler handler = new ServletHandler();
server.setHandler(handler);
handler.addServletWithMapping(SimpleGroovyServlet.class, "/*");
println "Starting Jetty, press Ctrl+C to stop."
server.start()
server.join();