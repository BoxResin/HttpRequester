package util;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

/** Http 프로토콜로 서버에 데이터를 요청하는 유틸리티 클래스 */
public class HttpRequester
{
	private static final String LOG_TAG = "util.HttpRequester";

	/** 데이터를 수신하는 도중 발생할 수 있는 오류 */
	public enum Error
	{
		OK,            // 오류없음
		TIMEOUT,       // 시간초과
		NETWORK,       // 네트워크 문제 (주로 인터넷에 연결되지 않았을 때 발생)
		INCORRECT_URL  // 잘못된 서버 주소
	}

	/** 데이터 요청방법 */
	private enum Method
	{
		GET, // Get 방식
		POST // Post 방식
	}

	/** 요청했던 데이터를 받기위한 리스너 */
	public interface HttpResultListener
	{
		/**
		 * 데이터를 받았을 때 UI 스레드에서 호출되는 메서드
		 * @param data  전달된 데이터
		 * @param error 데이터 수신 도중 발생한 오류
		 */
		void onHttpResult(String data, Error error);
	}

	private String address;          // 연결된 서버주소
	private RequestTask currentTask; // 현재 진행중인 데이터 요청작업

	/** 빈 주소를 가지는 생성자 */
	public HttpRequester()
	{
		this("");
	}

	/**
	 * 데이터를 요청할 서버의 주소를 전달받는 생성자
	 * @param address 서버의 주소
	 */
	public HttpRequester(String address)
	{
		setAddress(address);
	}

	/**
	 * 백그라운드에서 Get방식으로 서버에 데이터를 요청하는 메서드
	 * @param listener 요청한 데이터를 받을 리스너
	 */
	public void requestGet(HttpResultListener listener)
	{
		requestGet(0, listener);
	}

	/**
	 * 백그라운드에서 Get방식으로 서버에 데이터를 요청하는 메서드
	 * @param timeout  제한시간 (단위: millisecond)
	 * @param listener 요청한 데이터를 받을 리스너
	 */
	public void requestGet(int timeout, HttpResultListener listener)
	{
		request(Method.GET, "", timeout, listener);
	}

	/**
	 * 백그라운드에서 Post방식으로 서버에 데이터를 요청하는 메서드
	 * @param params   파라미터 (par=abc&par2=efg 형식으로 전달)
	 * @param listener 요청한 데이터를 받을 리스너
	*/
	public void requestPost(String params, HttpResultListener listener)
	{
		requestPost(params, 0, listener);
	}

	/**
	 * 백그라운드에서 Post방식으로 서버에 데이터를 요청하는 메서드
	 * @param params   파라미터 (par=abc&par2=efg 형식으로 전달)
	 * @param timeout  제한시간 (단위: millisecond)
	 * @param listener 요청한 데이터를 받을 리스너
	 */
	public void requestPost(String params, int timeout, HttpResultListener listener)
	{
		request(Method.POST, params, timeout, listener);
	}

	private void request(Method method, String params, int timeout, HttpResultListener listener)
	{
		// 현재 진행중인 요청작업이 완료되지 않았으면 취소한다.
		if (currentTask != null && currentTask.getStatus() != AsyncTask.Status.FINISHED)
			Log.v(LOG_TAG, "cancell 메서드 성공 여부 : " + currentTask.cancel(true));

		// 요청작업을 수행한다.
		currentTask = new RequestTask(method, params, timeout, listener);
		currentTask.execute();
	}

	/** 백그라운드에서 요청작업을 수행하는 클래스 */
	private class RequestTask extends AsyncTask<Void, Void, String>
	{
		private Method method;
		private String params;
		private int timeout;
		private HttpResultListener listener;

		private Error error = Error.OK; // 작업 도중 발생한 오류

		/** HttpRequester.request의 매개변수를 전달받기 위한 생성자 */
		private RequestTask(Method method, String params, int timeout, HttpResultListener listener)
		{
			this.method = method;
			this.params = params;
			this.timeout = timeout;
			this.listener = listener;
		}

		/**
		 * 백그라운드 스레드에서 실행되는 메서드
		 * @return : 서버에 요청한 데이터
		 */
		@Override
		protected String doInBackground(Void... v)
		{
			StringBuilder result = new StringBuilder();

			try
			{
				URL url = new URL(address);
				HttpURLConnection con = (HttpURLConnection) url.openConnection();
				con.setConnectTimeout(timeout);
				con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

				// POST 방식이면 파라미터를 추가한다.
				if (method == Method.POST)
				{
					con.setRequestMethod("POST");
					con.setDoOutput(true);

					OutputStream os = con.getOutputStream();
					os.write(params.getBytes("UTF-8"));
					os.flush();
					os.close();
				}

				// 결과를 읽어온다.
				String buffer;
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				while ((buffer = in.readLine()) != null)
					result.append(buffer);

				in.close();
			}

			// 네트워크 연결시간초과 예외
			catch (SocketTimeoutException e)
			{
				error = Error.TIMEOUT;
			}

			// 잘못된 URL 주소 예외
			catch (IllegalArgumentException e)
			{
				error = Error.INCORRECT_URL;
			}
			catch (IllegalStateException e)
			{
				error = Error.INCORRECT_URL;
			}

			// 기타 네트워크 예외
			catch (IOException e)
			{
				error = Error.NETWORK;
			}

			// 무시할만한 예외
			catch (RuntimeException ignored)
			{
			}

			return result.toString();
		}

		/**
		 * 백그라운드 작업이 끝난 후 UI 스레드에서 실행되는 메서드
		 * @param result : doInBackground가 반환한 결과 값
		 */
		@Override
		protected void onPostExecute(String result)
		{
			// 작업이 취소되지 않았을때만 리스너에 결과데이터와 작업도중 발생했던 오류코드를 전달한다.
			if (!isCancelled())
				listener.onHttpResult(result, error);
		}

		@Override
		protected void onCancelled()
		{
			Log.v(LOG_TAG, "onCancelled(); 호출됨");
		}

		@Override
		protected void onCancelled(String s)
		{
			Log.v(LOG_TAG, "onCancelled(String); 호출됨");
		}
	}

	/**
	 * 현재 연결된 서버의 주소를 가져오는 메서드
	 * @return : 현재 연결된 서버의 주소
	 */
	public String getAddress()
	{
		return address;
	}

	/**
	 * 데이터를 요청할 서버의 주소를 설정하는 메서드
	 * @param address : 데이터를 요청할 서버의 주소
	 */
	public void setAddress(String address)
	{
		this.address = address;
	}
}