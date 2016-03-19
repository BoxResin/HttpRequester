package app.boxresin.httprequestertest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import util.HttpRequester;

public class MainActivity extends AppCompatActivity
{

	private TextView text;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		text = (TextView) findViewById(R.id.text);

		HttpRequester requester = new HttpRequester("http://dic.naver.com/search.nhn");
		requester.requestPost("query=아", new HttpRequester.HttpResultListener()
		{
			@Override
			public void onHttpResult(String data, HttpRequester.Error error)
			{
				if (error == HttpRequester.Error.OK)
					text.setText(data);
			}
		});
	}
}