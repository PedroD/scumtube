package youtube.to.mp3;

public final class Activator {

	public static void main(String[] args) throws Exception {
//		Engine.setLogLevel(Level.ALL);
		RestWrapper.open();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					RestWrapper.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

}
