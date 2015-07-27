package youtube.to.mp3;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

	public static final int LOG_DEBUG = 3;
	public static final int LOG_ERROR = 0;
	public static final int LOG_INFO = 2;
	public static final int LOG_WARNING = 1;

	@SuppressWarnings("unused")
	private static final FileLogger fl = new FileLogger();

	private String getCurrentTime() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
		Date date = new Date();
		return " " + dateFormat.format(date) + " - ";
	}

	public void log(int logType, String msg) {
		System.out.println(getCurrentTime() + " " + msg);
	}

}
