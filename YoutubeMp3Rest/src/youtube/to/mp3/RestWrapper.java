package youtube.to.mp3;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;

public class RestWrapper extends
		Application {

	private static final String VIDEO_URL = "/video_id/{video_id}";

	private static final RestWrapper singleton = new RestWrapper();

	private final static int serverPort = 9194;

	private static Component component;

	private RestWrapper() {
	}

	public static RestWrapper getInstance() {
		return singleton;
	}

	/**
	 * Method to log events for this plugin instance.
	 * 
	 * @param debugMessage
	 *            the debug message
	 */
	static void logDebug(String debugMessage) {
		new Logger().log(Logger.LOG_DEBUG, "[BACKYT REST API] " + debugMessage);
	}

	/**
	 * Method to log events for this plugin instance.
	 * 
	 * @param errorMessage
	 *            the error message
	 */
	static void logError(String errorMessage) {
		new Logger().log(Logger.LOG_ERROR, "[BACKYT REST API] " + errorMessage);
	}

	/**
	 * Method to log events for this plugin instance.
	 * 
	 * @param infoMessage
	 *            the info message
	 */
	static void logInfo(String infoMessage) {
		new Logger().log(Logger.LOG_INFO, "[BACKYT REST API] " + infoMessage);
	}

	/**
	 * Method to log events for this plugin instance.
	 * 
	 * @param warningMessage
	 *            the warning message
	 */
	static void logWarning(String warningMessage) {
		new Logger().log(Logger.LOG_WARNING, "[BACKYT REST API] " + warningMessage);
	}

	/**
	 * Creates a root Restlet which will receive all incoming calls.
	 * 
	 * @return the routing schema
	 */
	@Override
	public synchronized Restlet createInboundRoot() {
		final Router router = new Router(getContext());

		router.attach(VIDEO_URL, ServicesProvider.DownloadMP3.class);

		return router;
	}

	private static void serverStart() throws InterruptedException {
		// Create a new Component.
		component = new Component();

		// Add a new HTTP server listening on default port.
		component.getServers().add(Protocol.HTTP, serverPort);

		boolean serverBound = false;
		// Start the component.
		while (!serverBound) {
			try {
				logInfo("Bundle started, REST server initializing...");
				component.start();
				if (component.isStarted())
					logInfo("REST Server Started in port " + serverPort + ".");
				serverBound = true;
			} catch (Exception e) {
				try {
					component.stop();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				logError("Port " + serverPort + " is occupied! Trying again in 15 sec.");
				Thread.sleep(15000);
			}
		}
	}

	private static void serverAttach() {
		component.getDefaultHost().attach(RestWrapper.getInstance());
	}

	private static void printAvailableEndpoints() {
		final String addrStr = "    -> http://<hostname>:" + serverPort;
		logInfo("");
		logInfo("You can use this REST API by addressing the following GET/POST endpoints:");
		logInfo(addrStr + VIDEO_URL);
		logInfo("");
	}

	public static void open() throws Exception {
		new Thread() {
			public void run() {
				try {
					serverStart();
					serverAttach();
					printAvailableEndpoints();
				} catch (InterruptedException e) {
					logInfo("");
					logInfo("Starting server.");
				}
			}
		}.start();
	}

	public static void close() throws Exception {
		new Thread() {
			public void run() {
				logInfo("");
				logInfo("Stopping server.");
				try {
					component.stop();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
	}
}
