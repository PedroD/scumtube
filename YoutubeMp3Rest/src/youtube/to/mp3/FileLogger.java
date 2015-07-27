package youtube.to.mp3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The Class ConsoleLogger that logs into the parent terminal.
 * <p>
 * This logger makes use of {@link ConsoleLogger} which prints everything to the console and redirects the output to a file. This
 * is very useful because even uncaught exceptions will be part of the log file.
 */
public final class FileLogger {

	private static final String LOG_FILE_NAME = "YTMP3_Log";

	private final PrintStream console = System.out;

	private MutablePrintStream filePrintStream;

	private final class MutablePrintStream extends PrintStream {

		public MutablePrintStream(OutputStream out) {
			super(out);
		}

		public void setNewOutputStream(OutputStream out) {
			this.out = out;
		}

		public OutputStream getOutputStream() {
			return this.out;
		}
	}

	private final class DoubleOutputStream extends OutputStream {

		private OutputStream stream1;
		private OutputStream stream2;

		public DoubleOutputStream(final OutputStream stream1, final OutputStream stream2) {
			this.stream1 = stream1;
			this.stream2 = stream2;
		}

		@Override
		public void close() throws IOException {
			this.stream1.close();
			this.stream2.close();
		}

		@Override
		public void flush() throws IOException {
			this.stream1.flush();
			this.stream2.flush();
		}

		@Override
		public void write(int b) throws IOException {
			this.stream1.write(b);
			this.stream2.write(b);
		}
	}

	private class NewFileThread extends Thread {
		private String previousDate;

		public NewFileThread() {
			super("File Logger Thread");
			previousDate = getCurrentDate();
		}

		@Override
		public void run() {
			try {
				while (!this.isInterrupted() && this.isAlive() && !createNewLogFile()) {
					Thread.sleep(60 * 1000);
				}
				while (!this.isInterrupted() && this.isAlive()) {
					/*
					 * 5 Minutes
					 */
					Thread.sleep(5 * 60 * 1000);
					final String currDate = getCurrentDate();
					if (!previousDate.equals(currDate)) {
						new Logger().log(Logger.LOG_INFO, "Date changed. Creating a new log file.");
						if (createNewLogFile())
							previousDate = currDate;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public FileLogger() {
		new NewFileThread().start();
	}

	private boolean createNewLogFile() {
		try {
			final String logFileName = LOG_FILE_NAME + "_" + getCurrentDate() + ".txt";
			final File file = new File(logFileName);
			final FileOutputStream fos = new FileOutputStream(file, true);
			final OutputStream newFileOutputStream = new DoubleOutputStream(fos, console);

			if (filePrintStream != null) {
				filePrintStream.getOutputStream().flush();
				filePrintStream.getOutputStream().close();
				filePrintStream.setNewOutputStream(newFileOutputStream);
			} else {
				filePrintStream = new MutablePrintStream(newFileOutputStream);
			}

			System.setOut(filePrintStream);
			System.setErr(filePrintStream);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private String getCurrentDate() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date date = new Date();
		return dateFormat.format(date);
	}
}
