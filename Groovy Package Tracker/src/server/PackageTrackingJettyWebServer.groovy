package server
@Grab(group='org.eclipse.jetty.aggregate', module='jetty-all', version='7.6.15.v20140411')
import org.eclipse.jetty.server.*
import org.eclipse.jetty.servlet.*
import geo.*
import accounts.*
import accounts.AccountManager
import geo.Coordinate
import geo.TrackablePackage
import groovy.json.*
import javax.servlet.http.*
import javax.servlet.*

/**
 * Creates a server that manages packages<br>
 * Functionality:<br>
 *  Add new packages to the database (doGet)<br>
 *	Update package information (doPost)<br>
 *	User login (doPost)<br>
 *	Return user's associated packages (doPost)<br>
 *	Associate package with user (doPost)<br>
 */
class SimpleGroovyServlet extends HttpServlet {
	/**
	 * Hashmap of all tracked packages and their information. Key is the uuid of the package, Value is a trackable package object
	 */
	private HashMap<String, TrackablePackage> allTrackedPackages=new HashMap()
	/**
	 * Handles server doGet requests<br>
	 * Accepts path info types: /tracknewpackage , /logout, /help, /removePackage
	 */
	void doGet(HttpServletRequest req, HttpServletResponse resp){
		println("doGet")
		if(req.getPathInfo().equals("/tracknewpackage")) {
			trackNewPackage(req, resp)
		}
		if(req.getPathInfo().equals("/logout")){
			logout(req, resp)
		}
		if(req.getPathInfo().equals("/help")){
			help(req, resp)
		}
	}

	/**
	 * Handles server doPost request
	 * Accepts path info types: /login, /packages, /addPackage/.*, /packagetrackupdate/.*, /updateNotes/.*
	 */
	void doPost(HttpServletRequest req, HttpServletResponse resp) {
		if(req.getPathInfo().equals("/removePackage")){
			removePackage(req, resp)
		}
		if(req.getPathInfo().equals("/login")){
			userLogin(req,resp)
		}
		if(req.getPathInfo().equals("/packages")){
			getPackages(req,resp)
		}

		if(req.getPathInfo().startsWith("/addPackage")) {
			addPackage(req,resp)
		}

		if(req.getPathInfo().startsWith("/packagetrackupdate")) {
			packageTrackUpdate(req,resp)
		}

		if(req.getPathInfo().startsWith("/updateNotes")) {
			updateNotes(req,resp)
		}
		if(req.getPathInfo().startsWith("/addAccount")){
			createAccount(req,resp)
		}
	}
	private void createAccount(HttpServletRequest req, HttpServletResponse resp){
		//Gets the username and password from the request
		//Uses cookies to score username
		String username = req.getParameter("username")
		String password = req.getParameter("password")
		AccountManager.restoreAccountsFromFile()
		String response
		//checks if the username does not exist or password is incorrect
		if(AccountManager.getAccounts().containsKey(username)){
			response="userNameTaken"
		}
		else{
			AccountManager.addUserAccount(username,password,false);
			AccountManager.backUpAccountList();
			println "Welcome user "+username;
		}
		def writer = resp.getWriter()
		resp.setContentType("text/plain")
		writer.print(response)
	}
	/**
	 * Records a new package and adds it to the package hashmap, using the packages UUID
	 * as a key and the object as the value	
	 * @param req The server request, contains new package information
	 * @param resp The server response
	 */
	private void trackNewPackage(HttpServletRequest req, HttpServletResponse resp){
		def uuids = req.getParameterMap().get("uuid")
		def responseString = "{ \"ackUUID\":\""+uuids+"\" }"
		double lat=Double.parseDouble(req.getParameterMap().get("destinationLat")[0])
		double lon=Double.parseDouble(req.getParameterMap().get("destinationLon")[0])
		println responseString
		//Creates a new package
		allTrackedPackages.putAt(uuids[0],new TrackablePackage(uuids[0], new Coordinate(lat,lon)))
		resp.setContentType("application/json")
		def writer = resp.getWriter()
		writer.print(responseString)
		writer.flush()
	}

	/**
	 * Logs the user out by deleting all serverside information on the user
	 * @param req The server request, contains user cookie
	 * @param resp The server response, does not contain user cookie
	 */
	private void logout(HttpServletRequest req, HttpServletResponse resp){
		def info = req.getCookies()
		def cookie = info[0]
		cookie.setMaxAge(0);
		resp.addCookie(cookie);
	}

	/**
	 * Return the help document to display
	 * @param req The server request
	 * @param resp The server response, contains help document information
	 */
	private void help(HttpServletRequest req, HttpServletResponse resp){
		String helpText = returnText("Text/helpText")
		def writer = resp.getWriter()
		resp.setContentType("text/plain")
		writer.print(helpText)
	}
	
	/**
	 * Return the help document to display
	 * @param req The server request, contains UUID of package to delete and user to remove from
	 * @param resp The server response
	 */
	private void removePackage(HttpServletRequest req, HttpServletResponse resp){
		def uuid = req.getParameter("uuid")
		def user = req.getCookies()[0].value
		AccountManager.getAccounts().get(user).removePackage(uuid)
	}
	
	
	/**
	 * Authorizes the user, and, if confirmed, adds a cookie of the user's username
	 * <br>Prints out to resp an error message in different cases:
	 * @param req The server request, contains username and password
	 * @param resp The server response, contains new cookie or a response that is either the string "mismatch" or the string "blankfield"
	 */
	private void userLogin(HttpServletRequest req, HttpServletResponse resp){
		//Gets the username and password from the request
		//Uses cookies to score username
		String username = req.getParameter("username")
		String password = req.getParameter("password")
		AccountManager.restoreAccountsFromFile()
		def accounts = AccountManager.getAccounts()
		String response
		//Checks for a null field
		if(username == "" || password == ""){
			response=("blankfield")
		}
		//checks if the username does not exist or password is incorrect
		else if(!(accounts.containsKey(username))  || accounts.get(username).getPassword()!= password){
			response="mismatch"
		}
		else{
			//log the user into the web page
			println "new Login from user "+username
			Cookie user = new Cookie("username", username)
			resp.addCookie(user)
		}
		def writer = resp.getWriter()
		resp.setContentType("text/plain")
		writer.print(response)
	}

	/**
	 * Sends back the user's associated packages in the response
	 * @param req The server request, contains user cookie
	 * @param resp The server response, contains JSON of the packages or the string "noUser" when there is not a current username cookie
	 */
	private void getPackages(HttpServletRequest req, HttpServletResponse resp){
		def username
		def writer = resp.getWriter()
		try{
			def info = req.getCookies()
			username = info[0].getValue()
		}
		catch(ArrayIndexOutOfBoundsException e){
			writer.print("noUser") //no user logged in, redirect to login page
			return
		}
		//Creates list of packages either entered by the user previously or now
		HashSet<TrackablePackage> packageInfos
		//if this user exists
		def accounts = AccountManager.getAccounts()
		if(accounts.containsKey(username)){ //if username exists
			if(accounts.get(username).isAdmin()){//if user is an admin
				packageInfos=allTrackedPackages.values()//return all currently tracked packages
			}else{//if user is a regular user
				packageInfos=accounts.get(username).getTrackedPackages().values()//return that user's tracked packages
			}
		}else{
			writer.print("noUser")// username is invalid, redirect to login page
			return
		}
		//Returns packages
		resp.setContentType("application/json")
		def toJson = JsonOutput.toJson(packageInfos)
		writer.print(toJson)
		writer.flush()
	}

	/**
	 * Associates packages with a user to be displayed later
	 * @param req The server request, contains new package UUIDs to add and user cookie
	 * @param resp The server response, contains all packages asociated with user cookie, including new one
	 */
	private void addPackage(HttpServletRequest req, HttpServletResponse resp){
		def info = req.getCookies()
		def username
		try{
			username=info[0].getValue()
		}
		catch(ArrayIndexOutOfBoundsException e){
			return
		}
		def accounts = AccountManager.getAccounts()
		def uuid = req.getParameter("uuid") //Not sure if this will work. If errors, look here
		if(allTrackedPackages.containsKey(uuid)&&accounts.containsKey(username)){
			accounts.get(username).addPackage(allTrackedPackages.get(uuid))
		}
	}

	/**
	 * Associates packages with a user to be displayed later
	 * @param req The server request, contains new package UUIDs to add and user cookie
	 * @param resp The server response, contains all packages asociated with user cookie, including new one
	 */
	private void updateNotes(HttpServletRequest req, HttpServletResponse resp){
		def uuid = req.getParameter("uuid")
		def notes = req.getParameter("notes")
		def currentPackage = allTrackedPackages.get(uuid)
		currentPackage.setNotes(notes)
	}

	/**
	 * Updates a specific package's location and delivery status. 
	 * @param req The server request, contains package update information
	 * @param resp The server response
	 */
	private void packageTrackUpdate(HttpServletRequest req, HttpServletResponse resp){
		try {
			BufferedReader reader = req.getReader()
			String line = null
			while ((line = reader.readLine()) != null) {
				def slurper=new JsonSlurper()
				def inf=slurper.parseText(line)
				def uuid = req.getPathInfo().replace("/packagetrackupdate/","")
				TrackablePackage currentPackage = allTrackedPackages.get(uuid)
				if(line.contains("delivered")) {
					//This code registers delivery events
					println uuid +" -> "+ line
					currentPackage.setDelivered(true)
				}
				else{
					//This code tracks all non delivery events
					currentPackage.update(new Coordinate(Double.parseDouble(inf.lat),Double.parseDouble(inf.lon),Double.parseDouble(inf.ele)),inf.time)
				}
			}
		} catch (Exception e) { e.printStackTrace() /*report an error*/ }
	}


}
AccountManager.initAccounts()
//Starts the server on port 8000
def server = new Server(8000)
ServletHandler handler = new ServletHandler()
server.setHandler(handler)
handler.addServletWithMapping(SimpleGroovyServlet.class, "/*")
println "Starting Jetty, press Ctrl+C to stop."
server.start()
server.join()
