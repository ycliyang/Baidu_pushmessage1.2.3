package com.ly.biaoju.msg.baidupush.client;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.baidu.android.pushservice.PushConstants;
import com.baidu.android.pushservice.PushManager;
import com.baidu.frontia.api.FrontiaPushMessageReceiver;
import com.google.gson.Gson;
import com.ly.biaoju.R;
import com.ly.biaoju.msg.activity.MainActivity;
import com.ly.biaoju.msg.app.PushApplication;
import com.ly.biaoju.msg.baidupush.BaiduUtils;
import com.ly.biaoju.msg.bean.Message;
import com.ly.biaoju.msg.bean.MessageItem;
import com.ly.biaoju.msg.bean.RecentItem;
import com.ly.biaoju.msg.bean.User;
import com.ly.biaoju.msg.common.util.L;
import com.ly.biaoju.msg.common.util.NetUtil;
import com.ly.biaoju.msg.common.util.SendMsgAsyncTask;
import com.ly.biaoju.msg.common.util.SharePreferenceUtil;
import com.ly.biaoju.msg.common.util.T;

/**
 * Push消息处理receiver。请编写您需要的回调函数， 一般来说： onBind是必须的，用来处理startWork返回值；
 * onMessage用来接收透传消息； onSetTags、onDelTags、onListTags是tag相关操作的回调；
 * onNotificationClicked在通知被点击时回调； onUnbind是stopWork接口的返回值回调
 * 
 * 返回值中的errorCode，解释如下： 
 *  0 - Success
 *  10001 - Network Problem
 *  30600 - Internal Server Error
 *  30601 - Method Not Allowed 
 *  30602 - Request Params Not Valid
 *  30603 - Authentication Failed 
 *  30604 - Quota Use Up Payment Required 
 *  30605 - Data Required Not Found 
 *  30606 - Request Time Expires Timeout 
 *  30607 - Channel Token Timeout 
 *  30608 - Bind Relation Not Found 
 *  30609 - Bind Number Too Many
 * 
 * 当您遇到以上返回错误时，如果解释不了您的问题，请用同一请求的返回值requestId和errorCode联系我们追查问题。
 * 
 */
public class PushMessageReceiver extends FrontiaPushMessageReceiver {
    /** TAG to Log */
    public static final String TAG = PushMessageReceiver.class
            .getSimpleName();

	public static final int NOTIFY_ID = 0x000;
	public static int mNewNum = 0;// 通知栏新消息条目，我只是用了一个全局变量，
	public static final String RESPONSE = "response";
	public static ArrayList<EventHandler> ehList = new ArrayList<EventHandler>();
	
	public static String ONBIND="onBind";

	public static abstract interface EventHandler {
		public abstract void onMessage(Message message);

		public abstract void onBind(String method, int errorCode, String content);

		public abstract void onNotify(String title, String content);

		public abstract void onNetChange(boolean isNetConnected);

		public void onNewFriend(User u);
	}
    

    /**
     * 调用PushManager.startWork后，sdk将对push
     * server发起绑定请求，这个过程是异步的。绑定请求的结果通过onBind返回。 如果您需要用单播推送，需要把这里获取的channel
     * id和user id上传到应用server中，再调用server接口用channel id和user id给单个手机或者用户推送。
     * 
     * @param context
     *            BroadcastReceiver的执行Context
     * @param errorCode
     *            绑定接口返回值，0 - 成功
     * @param appid
     *            应用id。errorCode非0时为null
     * @param userId
     *            应用user id。errorCode非0时为null
     * @param channelId
     *            应用channel id。errorCode非0时为null
     * @param requestId
     *            向服务端发起的请求id。在追查问题时有用；
     * @return none
     */
    @Override
    public void onBind(Context context, int errorCode, String appid,
            String userId, String channelId, String requestId) {
        String responseString = "onBind errorCode=" + errorCode + " appid="
                + appid + " userId=" + userId + " channelId=" + channelId
                + " requestId=" + requestId;
        Log.d(TAG, responseString);

        // 绑定成功，设置已绑定flag，可以有效的减少不必要的绑定请求
        if (errorCode == 0) {
            BaiduUtils.setBind(context, true);
        }
		paraseContent(context, errorCode, appid,userId, channelId, requestId);// 处理消息

		// 回调函数
		for (int i = 0; i < ehList.size(); i++)
			((EventHandler) ehList.get(i)).onBind(ONBIND, errorCode,null);
    }

    /**
     * 接收透传消息的函数。
     * 
     * @param context
     *            上下文
     * @param message
     *            推送的消息
     * @param customContentString
     *            自定义内容,为空或者json字符串
     */
    @Override
    public void onMessage(Context context, String message,
            String customContentString) {
        String messageString = "透传消息 message=\"" + message
                + "\" customContentString=" + customContentString;
        Log.d(TAG, messageString);
        
		// 消息的用户自定义内容读取方式
		L.i("onMessage: " + message);
		try {
			Message msgItem = PushApplication.getInstance().getGson().fromJson(message, Message.class);
			parseMessage(msgItem);// 预处理，过滤一些消息，比如说新人问候或自己发送的
		} catch (Exception e) {
			// TODO: handle exception
		}
        

    }

    /**
     * 接收通知点击的函数。注：推送通知被用户点击前，应用无法通过接口获取通知的内容。
     * 
     * @param context
     *            上下文
     * @param title
     *            推送的通知的标题
     * @param description
     *            推送的通知的描述
     * @param customContentString
     *            自定义内容，为空或者json字符串
     */
    @Override
    public void onNotificationClicked(Context context, String title,
            String description, String customContentString) {
        String notifyString = "通知点击 title=\"" + title + "\" description=\""
                + description + "\" customContent=" + customContentString;
        Log.d(TAG, notifyString);
        
        
    }

    /**
     * setTags() 的回调函数。
     * 
     * @param context
     *            上下文
     * @param errorCode
     *            错误码。0表示某些tag已经设置成功；非0表示所有tag的设置均失败。
     * @param successTags
     *            设置成功的tag
     * @param failTags
     *            设置失败的tag
     * @param requestId
     *            分配给对云推送的请求的id
     */
    @Override
    public void onSetTags(Context context, int errorCode,
            List<String> sucessTags, List<String> failTags, String requestId) {
        String responseString = "onSetTags errorCode=" + errorCode
                + " sucessTags=" + sucessTags + " failTags=" + failTags
                + " requestId=" + requestId;
        Log.d(TAG, responseString);

    }

    /**
     * delTags() 的回调函数。
     * 
     * @param context
     *            上下文
     * @param errorCode
     *            错误码。0表示某些tag已经删除成功；非0表示所有tag均删除失败。
     * @param successTags
     *            成功删除的tag
     * @param failTags
     *            删除失败的tag
     * @param requestId
     *            分配给对云推送的请求的id
     */
    @Override
    public void onDelTags(Context context, int errorCode,
            List<String> sucessTags, List<String> failTags, String requestId) {
        String responseString = "onDelTags errorCode=" + errorCode
                + " sucessTags=" + sucessTags + " failTags=" + failTags
                + " requestId=" + requestId;
        Log.d(TAG, responseString);

        // Demo更新界面展示代码，应用请在这里加入自己的处理逻辑
//        updateContent(context, responseString);
    }

    /**
     * listTags() 的回调函数。
     * 
     * @param context
     *            上下文
     * @param errorCode
     *            错误码。0表示列举tag成功；非0表示失败。
     * @param tags
     *            当前应用设置的所有tag。
     * @param requestId
     *            分配给对云推送的请求的id
     */
    @Override
    public void onListTags(Context context, int errorCode, List<String> tags,
            String requestId) {
        String responseString = "onListTags errorCode=" + errorCode + " tags="
                + tags;
        Log.d(TAG, responseString);

        // Demo更新界面展示代码，应用请在这里加入自己的处理逻辑
//        updateContent(context, responseString);
    }

    /**
     * PushManager.stopWork() 的回调函数。
     * 
     * @param context
     *            上下文
     * @param errorCode
     *            错误码。0表示从云推送解绑定成功；非0表示失败。
     * @param requestId
     *            分配给对云推送的请求的id
     */
    @Override
    public void onUnbind(Context context, int errorCode, String requestId) {
        String responseString = "onUnbind errorCode=" + errorCode
                + " requestId = " + requestId;
        Log.d(TAG, responseString);

        // 解绑定成功，设置未绑定flag，
        if (errorCode == 0) {
            BaiduUtils.setBind(context, false);
        }
        // Demo更新界面展示代码，应用请在这里加入自己的处理逻辑
//        updateContent(context, responseString);
    }


    
    /*************************************/


	/**
	 * 处理登录结果
	 * 
	 * @param errorCode
	 * @param content
	 */
	private void paraseContent(final Context context, int errorCode, String appid,
            String userId, String channelId, String requestId) {
		// TODO Auto-generated method stub
		if (errorCode == 0) {
			SharePreferenceUtil util = PushApplication.getInstance().getSpUtil();
			util.setAppId(appid);
			util.setChannelId(channelId);
			util.setUserId(userId);
		} else {
			if (NetUtil.isNetConnected(context)) {
				if (errorCode == 30607) {
					T.showLong(context, "账号已过期，请重新登录");
					// 跳转到重新登录的界面
				} else {
					T.showLong(context, "启动失败，正在重试...");
					new Handler().postDelayed(new Runnable() {

						@Override
						public void run() {
							// TODO Auto-generated method stub
							PushManager.startWork(context,
									PushConstants.LOGIN_TYPE_API_KEY,
									PushApplication.API_KEY);
						}
					}, 2000);// 两秒后重新开始验证
				}
			} else {
				T.showLong(context, R.string.net_error_tip);
			}
		}
	}
    
	
	private void parseMessage(Message msg) {
		Gson gson = PushApplication.getInstance().getGson();
		// Message msg = gson.fromJson(message, Message.class);
		L.i("gson ====" + msg.toString());
		String tag = msg.getTag();
		String userId = msg.getUser_id();
		int headId = msg.getHead_id();
		// try {
		// headId = Integer.parseInt(JsonUtil.getFromUserHead(message));
		// } catch (Exception e) {
		// L.e("head is not a Integer....");
		// }
		if (!TextUtils.isEmpty(tag)) {// 如果是带有tag的消息
			if (userId.equals(PushApplication.getInstance().getSpUtil()
					.getUserId()))
				return;
			User u = new User(userId, msg.getChannel_id(), msg.getNick(),
					headId, 0);
			PushApplication.getInstance().getUserDB().addUser(u);// 存入或更新好友
			for (EventHandler handler : ehList)
				handler.onNewFriend(u);
			if (!tag.equals(RESPONSE)) {
				// Intent intenService = new
				// Intent(PushApplication.getInstance(),
				// PreParseService.class);
				// intenService.putExtra("message", message);
				// PushApplication.getInstance().startService(intenService);//
				// 启动服务去回消息
				// L.i("启动服务回复消息");
				L.i("response start");
				Message item = new Message(System.currentTimeMillis(), "hi",
						PushMessageReceiver.RESPONSE);
				new SendMsgAsyncTask(gson.toJson(item), userId).send();// 同时也回一条消息给对方1
				L.i("response end");
			}
		} else {// 普通消息，
			if (PushApplication.getInstance().getSpUtil().getMsgSound())// 如果用户开启播放声音
				PushApplication.getInstance().getMediaPlayer().start();
			if (ehList.size() > 0) {// 有监听的时候，传递下去
				for (int i = 0; i < ehList.size(); i++)
					((EventHandler) ehList.get(i)).onMessage(msg);
			} else {
				// 通知栏提醒，保存数据库
				// show notify
				showNotify(msg);
				MessageItem item = new MessageItem(
						MessageItem.MESSAGE_TYPE_TEXT, msg.getNick(),
						System.currentTimeMillis(), msg.getMessage(), headId,
						true, 1);
				RecentItem recentItem = new RecentItem(userId, headId,
						msg.getNick(), msg.getMessage(), 0,
						System.currentTimeMillis());
				PushApplication.getInstance().getMessageDB()
						.saveMsg(userId, item);
				PushApplication.getInstance().getRecentDB()
						.saveRecent(recentItem);
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	private void showNotify(Message message) {
		// TODO Auto-generated method stub
		mNewNum++;
		// 更新通知栏
		PushApplication application = PushApplication.getInstance();

		int icon = R.drawable.notify_newmessage;
		CharSequence tickerText = message.getNick() + ":"
				+ message.getMessage();
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);

		notification.flags = Notification.FLAG_NO_CLEAR;
		// 设置默认声音
		// notification.defaults |= Notification.DEFAULT_SOUND;
		// 设定震动(需加VIBRATE权限)
		notification.defaults |= Notification.DEFAULT_VIBRATE;
		notification.contentView = null;

		Intent intent = new Intent(application, MainActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(application, 0,
				intent, 0);
		notification.setLatestEventInfo(PushApplication.getInstance(),
				application.getSpUtil().getNick() + " (" + mNewNum + "条新消息)",
				tickerText, contentIntent);
		// 下面是4.0通知栏api
		// Bitmap headBm = BitmapFactory.decodeResource(
		// application.getResources(), PushApplication.heads[Integer
		// .parseInt(JsonUtil.getFromUserHead(message))]);
		// Notification.Builder mNotificationBuilder = new
		// Notification.Builder(application)
		// .setTicker(tickerText)
		// .setContentTitle(JsonUtil.getFromUserNick(message))
		// .setContentText(JsonUtil.getMsgContent(message))
		// .setSmallIcon(R.drawable.notify_newmessage)
		// .setLargeIcon(headBm).setWhen(System.currentTimeMillis())
		// .setContentIntent(contentIntent);
		// Notification n = mNotificationBuilder.getNotification();
		// n.flags |= Notification.FLAG_NO_CLEAR;
		//
		// n.defaults |= Notification.DEFAULT_VIBRATE;

		application.getNotificationManager().notify(NOTIFY_ID, notification);// 通知一下才会生效哦
	}

	
}
