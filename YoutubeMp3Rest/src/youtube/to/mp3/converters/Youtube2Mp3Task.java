package youtube.to.mp3.converters;

import org.json.JSONObject;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import youtube.to.mp3.Logger;
import youtube.to.mp3.ServicesProvider.VideoRequest;
import youtube.to.mp3.Task;

public final class Youtube2Mp3Task implements Task {

	private final VideoRequest vr;

	public Youtube2Mp3Task(VideoRequest vr) {
		this.vr = vr;
	}
	
	@Override
	public String toString() {
		return "Youtube2Mp3Task";
	}



	@Override
	public boolean run() {
		WebClient webClient = null;
		try {
			/*
			 * Initializing WebClient
			 */
			new Logger().log(Logger.LOG_INFO, "Initializing WebClient.");
			webClient = new WebClient(BrowserVersion.FIREFOX_38);
			webClient.setAjaxController(new NicelyResynchronizingAjaxController());
			webClient.getOptions().setJavaScriptEnabled(true);
			webClient.getOptions().setRedirectEnabled(true);
			new Logger().log(Logger.LOG_ERROR, "Finished initializing WebClient.");

			new Logger().log(Logger.LOG_INFO, "Request (TheYouMp3): Started making request.");
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
				new Logger().log(Logger.LOG_INFO, "Request (TheYouMp3): Finished making request.");
			} catch (Exception e) {
				webClient.close();
				new Logger().log(Logger.LOG_ERROR, "Error scraping the Youtube2Mp3 website. " + e.getMessage());
				return false;
			}

			/*
			 * While waiting for one response
			 */
			JSONObject theYouMp3JsonObject;
			while (true) {
				new Logger().log(Logger.LOG_INFO, "Response: Waiting for responses.");

				try {
					new Logger().log(Logger.LOG_INFO, "Response (TheYouMp3): Started getting response.");
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
									"Response (TheYouMp3): Finished getting TheYouMp3 response with success.");
							return true;
						}
						new Logger().log(Logger.LOG_INFO, "Response (TheYouMp3):  Not ready yet.");
					} else {
						return false;
					}
				} catch (Exception e) {
					new Logger().log(Logger.LOG_ERROR, "Error scraping the Youtube2Mp3 website. " + e.getMessage());
					return false;
				}
				Thread.sleep(1000);
			}
		} catch (Exception e) {
			new Logger().log(Logger.LOG_ERROR, "Error scraping the website. " + e.getMessage());
			e.printStackTrace();
			if (webClient != null) {
				webClient.close();
			}
			return false;
		}
	}
}
