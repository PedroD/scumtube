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
			new Logger().log(Logger.LOG_INFO, "(" + this + ") Initializing WebClient.");
			webClient = new WebClient(BrowserVersion.FIREFOX_38);
			webClient.setAjaxController(new NicelyResynchronizingAjaxController());
			webClient.getOptions().setJavaScriptEnabled(true);
			webClient.getOptions().setRedirectEnabled(true);
			webClient.getOptions().setCssEnabled(false);

			HtmlPage youtube2Mp3Page = null;
			HtmlAnchor youtube2Mp3An = null;
			try {
				new Logger().log(Logger.LOG_INFO, "Request (" + this + "): Started making request.");
				youtube2Mp3Page = webClient.getPage("http://www.youtube2mp3.cc");
				final HtmlTextInput youtube2Mp3TextField = (HtmlTextInput) youtube2Mp3Page.getElementById("video");
				final HtmlButton youtube2Mp3Button = (HtmlButton) youtube2Mp3Page.getElementById("button");
				youtube2Mp3An = (HtmlAnchor) youtube2Mp3Page.getElementById("download");
				youtube2Mp3TextField.setValueAttribute("https://www.youtube.com/watch?v=" + vr.getVideoId());
				youtube2Mp3Button.click();
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
			new Logger().log(Logger.LOG_INFO, "Response (" + this + "): Waiting for response.");
			while (true) {
				try {
					new Logger().log(Logger.LOG_INFO, "Response (" + this + "): Waiting a bit more...");
					if (youtube2Mp3An.getAttribute("href").toString().contains("http")) {
						vr.setMp3Url(youtube2Mp3An.getAttribute("href").toString());
						vr.setCoverUrl("http://i.ytimg.com/vi/" + vr.getVideoId() + "/default.jpg");
						vr.setTitle(youtube2Mp3Page.getElementById("title").asText());
						webClient.close();
						new Logger().log(Logger.LOG_INFO,
								"Response (" + this + "): Finished getting MP3 with success!");
						return true;
					} else if (youtube2Mp3Page.getElementById("error") != null) {
						return false;
					}
					new Logger().log(Logger.LOG_INFO, "Response (" + this + "): MP3 not ready yet.");
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
