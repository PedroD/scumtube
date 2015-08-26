package youtube.to.mp3.converters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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
		return "ListenToYouTubeTask";
	}

	@Override
	public boolean run() {
		/*
		 * Initializing httpClient
		 */
		final HttpClient httpClient = HttpClientBuilder.create().build();
		try {
			/*
			 * Making request
			 */
			new Logger().log(Logger.LOG_INFO, "Request (" + this + "): Started making request.");
			final HttpPost request = new HttpPost("http://www.listentoyoutube.com/cc/conversioncloud.php");

			final ArrayList<NameValuePair> postParameters;
			postParameters = new ArrayList<NameValuePair>();
			postParameters
					.add(new BasicNameValuePair("mediaurl", "https://www.youtube.com/watch?v=" + vr.getVideoId()));
			postParameters.add(new BasicNameValuePair("client_urlmap", "none"));
			request.setEntity(new UrlEncodedFormEntity(postParameters));

			final HttpResponse requestResponse = httpClient.execute(request);
			final StringBuilder stringBuilder = new StringBuilder();

			// waiting for json response
			do {
				final HttpEntity httpEntity = requestResponse.getEntity();
				final InputStream inputStream = httpEntity.getContent();
				final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
				String line = bufferedReader.readLine();
				while (line != null) {
					stringBuilder.append(line);
					stringBuilder.append(" \n");
					line = bufferedReader.readLine();
				}
				bufferedReader.close();
				Thread.sleep(1000);
			} while (stringBuilder.toString().length() == 0);

			if (stringBuilder.toString().length() > 1) {

				// creating json response from string
				final String jsonString = stringBuilder.toString().substring(1);
				new Logger().log(Logger.LOG_INFO, this + "Response (" + this + "): " + jsonString);
				JSONObject jsonObject = new JSONObject(jsonString);

				/*
				 * While waiting for one response
				 */
				new Logger().log(Logger.LOG_INFO, "Response (" + this + "): Waiting for response.");
				if (jsonObject.has("statusurl")) {
					while (true) {
						try {
							new Logger().log(Logger.LOG_INFO, "Response (" + this + "): Waiting a bit more...");
							HttpGet statusGet = new HttpGet(jsonObject.get("statusurl").toString());

							// parsing xml string to Document
							String xmlString = httpClient.execute(statusGet, new BasicResponseHandler());
							DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
							DocumentBuilder builder;
							builder = factory.newDocumentBuilder();
							Document doc = builder.parse(new InputSource(new StringReader(xmlString)));

							Element conversioncloud = (Element) doc.getElementsByTagName("conversioncloud").item(0);

							if (conversioncloud != null) {
								Element status = (Element) conversioncloud.getElementsByTagName("status").item(0);
								if (status != null) {
									if (status.getAttribute("step").equals("finished")) {
										Element downloadurl = (Element) conversioncloud
												.getElementsByTagName("downloadurl").item(0);
										Element file = (Element) conversioncloud.getElementsByTagName("file").item(0);
										String title;
										if (file.getTextContent().length() > 3) {
											title = new String(file.getTextContent()
													.substring(0, file.getTextContent().length() - 4).getBytes(),
													Charset.forName("UTF-8"));
										} else {
											title = "There was an error parsing the title";
										}
										vr.setCoverUrl("http://i.ytimg.com/vi/" + vr.getVideoId() + "/default.jpg");
										vr.setTitle(title);
										vr.setMp3Url(downloadurl.getTextContent());
										return true;
									}
								}
							}
						} catch (Exception e) {
							if (!(e instanceof InterruptedException))
								e.printStackTrace();
							new Logger().log(Logger.LOG_ERROR,
									"Error scraping the " + this + " website. " + e.getMessage());
							return false;
						}
						Thread.sleep(1500);
					}
				}
			}

			return false;
		} catch (Exception e) {
			if (!(e instanceof InterruptedException))
				e.printStackTrace();
			new Logger().log(Logger.LOG_ERROR, "Error scraping the " + this + " website. " + e.getMessage());
			return false;
		}
	}
}
