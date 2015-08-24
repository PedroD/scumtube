package youtube.to.mp3.converters;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

import youtube.to.mp3.Logger;
import youtube.to.mp3.ServicesProvider.VideoRequest;
import youtube.to.mp3.Task;

public final class TheYouMp3Task implements Task {

	private final VideoRequest vr;

	public TheYouMp3Task(VideoRequest vr) {
		this.vr = vr;
	}
	
	@Override
	public String toString() {
		return "TheYouMp3Task";
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

			HtmlPage youtube2Mp3Page = null;
			HtmlAnchor youtube2Mp3An = null;
			try {
				new Logger().log(Logger.LOG_INFO, "Request (Youtube2Mp3): Started making request.");
				youtube2Mp3Page = webClient.getPage("http://www.youtube2mp3.cc");
				final HtmlTextInput youtube2Mp3TextField = (HtmlTextInput) youtube2Mp3Page.getElementById("video");
				final HtmlButton youtube2Mp3Button = (HtmlButton) youtube2Mp3Page.getElementById("button");
				youtube2Mp3An = (HtmlAnchor) youtube2Mp3Page.getElementById("download");
				youtube2Mp3TextField.setValueAttribute("https://www.youtube.com/watch?v=" + vr.getVideoId());
				youtube2Mp3Button.click();
				new Logger().log(Logger.LOG_INFO, "Request (Youtube2Mp3): Finished making request.");
			} catch (Exception e) {
				webClient.close();
				new Logger().log(Logger.LOG_ERROR, "Error scraping the Youtube2Mp3 website. " + e.getMessage());
				return false;
			}

			/*
			 * While waiting for one response
			 */
			while (true) {
				new Logger().log(Logger.LOG_INFO, "Response: Waiting for responses.");

				try {
					new Logger().log(Logger.LOG_INFO, "Response (Youtube2Mp3): Started getting response.");
					if (youtube2Mp3An.getAttribute("href").toString().contains("http")) {
						vr.setMp3Url(youtube2Mp3An.getAttribute("href").toString());
						vr.setCoverUrl("http://i.ytimg.com/vi/" + vr.getVideoId() + "/default.jpg");
						vr.setTitle(youtube2Mp3Page.getElementById("title").asText());
						webClient.close();
						new Logger().log(Logger.LOG_INFO,
								"Response (Youtube2Mp3): Finished getting TheYouMp3 response with success.");
						return true;
					} else if (youtube2Mp3Page.getElementById("error") != null) {
						return false;
					}
					new Logger().log(Logger.LOG_INFO, "Response (Youtube2Mp3):  Not ready yet.");
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
