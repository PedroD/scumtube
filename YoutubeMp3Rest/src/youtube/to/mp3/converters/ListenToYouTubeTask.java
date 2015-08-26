package youtube.to.mp3.converters;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import youtube.to.mp3.Logger;
import youtube.to.mp3.ServicesProvider.VideoRequest;
import youtube.to.mp3.Task;

public final class ListenToYouTubeTask implements Task {

	private final VideoRequest vr;

	public ListenToYouTubeTask(VideoRequest vr) {
		this.vr = vr;
	}

	@Override
	public String toString() {
		return "TheYouMp3Task";
	}

	@Override
	public boolean run() {
	    HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead 
		try {
			HttpPost request = new HttpPost("http://www.listentoyoutube.com/cc/conversioncloud.php?callback=jQuery17208709984118024622_1440600881734");
	        StringEntity params =new StringEntity("{\"mediaurl\":\"https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%" + vr.getVideoId() + "\",\"client_urlmap\":\"none\"}");
			new Logger().log(Logger.LOG_INFO, "{\"mediaurl\":\"https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%" + vr.getVideoId() + "\",\"client_urlmap\":\"none\"} ");
	        request.addHeader("content-type", "application/x-www-form-urlencoded");
	        request.setEntity(params);
	        HttpResponse response = httpClient.execute(request);
			new Logger().log(Logger.LOG_INFO, response.getEntity().toString());
			Thread.sleep(1000);
			return false;
		} catch (Exception e) {
			if (!(e instanceof InterruptedException))
				e.printStackTrace();
			new Logger().log(Logger.LOG_ERROR, "Error scraping the " + this + " website. " + e.getMessage());
			return false;
		}
	}
}
