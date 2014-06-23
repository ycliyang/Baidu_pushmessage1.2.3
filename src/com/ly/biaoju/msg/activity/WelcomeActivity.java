package com.ly.biaoju.msg.activity;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;

import com.ly.biaoju.R;
import com.ly.biaoju.msg.app.PushApplication;
import com.ly.biaoju.msg.common.util.SharePreferenceUtil;

public class WelcomeActivity extends Activity {
	private SharePreferenceUtil spUtil;
	private Handler handler;

	Runnable startAct = new Runnable() {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			if (!TextUtils.isEmpty(spUtil.getUserId())) {
				Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
				startActivity(intent);
			} else {
				startActivity(new Intent(WelcomeActivity.this, FirstSetActivity.class));
			}
			finish();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.splash);
		spUtil = PushApplication.getInstance().getSpUtil();
		handler = new Handler();
		handler.postDelayed(startAct, 3000);

	}

}
