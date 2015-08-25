package youtube.to.mp3.converters;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.IncorrectnessListener;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlParagraph;
import com.gargoylesoftware.htmlunit.javascript.HtmlUnitContextFactory;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import youtube.to.mp3.Logger;
import youtube.to.mp3.ServicesProvider.VideoRequest;
import youtube.to.mp3.Task;

public final class Mp3FiberTask implements Task {

	private final VideoRequest vr;

	public Mp3FiberTask(VideoRequest vr) {
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
			webClient = new WebClient(BrowserVersion.INTERNET_EXPLORER_11);
			webClient.setAjaxController(new NicelyResynchronizingAjaxController());
			final SilentCssErrorHandler eh = new SilentCssErrorHandler();
			webClient.setAjaxController(new NicelyResynchronizingAjaxController());
			webClient.getOptions().setJavaScriptEnabled(true);
			webClient.getOptions().setRedirectEnabled(true);
			webClient.getOptions().setCssEnabled(false);

			new Logger().log(Logger.LOG_INFO, "Request (" + this + "): Started making request.");
			HtmlPage mp3FiberPage = null;
			String mp3FiberHttpResponse = null;
			String requestUrl = "http://mp3fiber.com/include2/index.php?videoURL=https://youtu.be/" + vr.getVideoId()
					+ "&ftype=mp3&quality=320";
			String title;
			try {
				mp3FiberPage = webClient.getPage(requestUrl);
				final HtmlDivision div = (HtmlDivision) mp3FiberPage.getElementById("preview");

				final HtmlParagraph p = (HtmlParagraph) div.getElementsByTagName("p").get(2);
				title = p.asText();

				mp3FiberHttpResponse = mp3FiberPage.getBody().asXml();
				if (mp3FiberHttpResponse == null || mp3FiberHttpResponse.contains("Error downloading remote file!")) {
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
			new Logger().log(Logger.LOG_INFO, "Response (" + this + "): Waiting for response.");
			while (true) {
				try {
					new Logger().log(Logger.LOG_INFO, "Response (" + this + "): Waiting a bit more...");
					if (webClient.getCurrentWindow().getEnclosedPage().getUrl().toString().equals(requestUrl)) {
						new Logger().log(Logger.LOG_INFO, "Response (" + this + "): MP3 not ready yet.");
						Thread.sleep(1000);
						continue;
					}

					final HtmlPage redirectPage = (HtmlPage) webClient.getCurrentWindow().getEnclosedPage();

					HtmlDivision div = null;
					do {
						div = (HtmlDivision) redirectPage.getFirstByXPath("//div[@class='searchDiv']");
					} while (div == null);

					HtmlAnchor a = null;
					do {
						a = div.getFirstByXPath("//a[@onclick]");
					} while (a == null);

					vr.setCoverUrl("http://i.ytimg.com/vi/" + vr.getVideoId() + "/default.jpg");
					vr.setTitle(title);
					vr.setMp3Url("http://www.mp3fiber.com/" + a.getAttribute("href"));
					webClient.close();
					new Logger().log(Logger.LOG_INFO, "Response (" + this + "): Finished getting MP3 with success!");
					return true;

				} catch (Exception e) {
					if (!(e instanceof InterruptedException))
						e.printStackTrace();
					new Logger().log(Logger.LOG_ERROR, "Error scraping the " + this + " website. " + e.getMessage());
					return false;
				}
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
