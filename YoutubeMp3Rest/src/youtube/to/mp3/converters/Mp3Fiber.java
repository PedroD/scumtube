package youtube.to.mp3.converters;

import org.json.JSONObject;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import youtube.to.mp3.Logger;
import youtube.to.mp3.ServicesProvider.VideoRequest;
import youtube.to.mp3.Task;

public final class Mp3Fiber implements Task {

	private final VideoRequest vr;

	public Mp3Fiber(VideoRequest vr) {
		this.vr = vr;
	}

	@Override
	public String toString() {
		return "Mp3Fiber";
	}

	// http://mp3fiber.com/include2/index.php?videoURL=https://youtu.be/l8CUJkIwARs&ftype=mp3&quality=320
	
	@Override
	public boolean run() {
		WebClient webClient = null;
		try {
			/*
			 * Initializing WebClient
			 */
			new Logger().log(Logger.LOG_INFO, "(" + this + ") Initializing WebClient.");
			webClient = new WebClient(BrowserVersion.FIREFOX_38);
			webClient.setAjaxController(new NicelyResynchronizingAjaxController());
			webClient.getOptions().setJavaScriptEnabled(true);
			webClient.getOptions().setRedirectEnabled(true);

			new Logger().log(Logger.LOG_INFO, "Request (" + this + "): Started making request.");
			HtmlPage theYouMp3Page = null;
			String theYouMp3HttpResponse = null;
			try {
				theYouMp3Page = webClient.getPage(
						"http://www.theyoump3.com/a/pushItem/?item=https://www.youtube.com/watch?v=" + vr.getVideoId());
				theYouMp3HttpResponse = theYouMp3Page.getBody().asText();
				if (theYouMp3HttpResponse == null || theYouMp3HttpResponse.contains("ERROR")) {
					webClient.close();
					return false;
				}
			} catch (Exception e) {
				if (!(e instanceof InterruptedException))
					e.printStackTrace();
				webClient.close();
				new Logger().log(Logger.LOG_ERROR, "Error scraping the " + this + " website. " + e.getMessage());
				return false;
			}

			/*
			 * While waiting for one response
			 */
			JSONObject theYouMp3JsonObject;
			new Logger().log(Logger.LOG_INFO, "Response (" + this + "): Waiting for response.");
			while (true) {
				try {
					new Logger().log(Logger.LOG_INFO, "Response (" + this + "): Waiting a bit more...");
					theYouMp3Page = webClient
							.getPage("http://www.theyoump3.com/a/itemInfo/?video_id=" + vr.getVideoId());
					theYouMp3HttpResponse = theYouMp3Page.getBody().asText();
					if (theYouMp3HttpResponse == null || theYouMp3HttpResponse.contains("ERROR")) {
						return false;
					}
					theYouMp3HttpResponse = theYouMp3HttpResponse.substring(7);
					theYouMp3JsonObject = new JSONObject(theYouMp3HttpResponse);
					if (theYouMp3JsonObject.has("status")) {
						if (theYouMp3JsonObject.get("status").equals("serving")) {
							vr.setCoverUrl("http://i.ytimg.com/vi/" + vr.getVideoId() + "/default.jpg");
							vr.setTitle(theYouMp3JsonObject.getString("title"));
							vr.setMp3Url("http://www.theyoump3.com/get?ab=128&video_id=" + vr.getVideoId() + "&h="
									+ theYouMp3JsonObject.getString("h"));
							webClient.close();
							new Logger().log(Logger.LOG_INFO,
									"Response (" + this + "): Finished getting MP3 with success!");
							return true;
						}
						new Logger().log(Logger.LOG_INFO, "Response (" + this + "): MP3 not ready yet.");
					} else {
						return false;
					}
				} catch (Exception e) {
					if (!(e instanceof InterruptedException))
						e.printStackTrace();
					new Logger().log(Logger.LOG_ERROR, "Error scraping the " + this + " website. " + e.getMessage());
					return false;
				}
				Thread.sleep(1000);
			}
		} catch (Exception e) {
			if (!(e instanceof InterruptedException))
				e.printStackTrace();
			new Logger().log(Logger.LOG_ERROR, "Error scraping the " + this + " website. " + e.getMessage());
			if (webClient != null) {
				webClient.close();
			}
			return false;
		}
	}
}
