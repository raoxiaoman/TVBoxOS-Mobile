package com.github.tvbox.osc.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.player.controller.LiveNewController;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupNewAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemNewAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.ui.dialog.AllChannelsRightDialog;
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.github.tvbox.osc.ui.dialog.LiveSettingDialog;
import com.github.tvbox.osc.ui.dialog.LiveSettingRightDialog;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.ui.widget.LinearSpacingItemDecoration;
import com.github.tvbox.osc.ui.widget.PlayerMenuView;
import com.github.tvbox.osc.ui.widget.PlayerTitleView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.github.tvbox.osc.util.live.LiveStore;
import com.github.tvbox.osc.util.live.LiveSubscriptionUi;
import com.github.tvbox.osc.util.live.LiveCatalog;
import com.github.tvbox.osc.util.live.LiveRetry;
import com.google.gson.JsonArray;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import xyz.doikki.videocontroller.component.LiveControlView;
import xyz.doikki.videocontroller.component.TitleView;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveActivity extends BaseActivity {
    public static Context context;
    private VideoView mVideoView;
    private TextView tvChannelInfo;
    private LinearLayout tvLeftChannelListLayout;
    private RecyclerView mChannelGroupView;
    private RecyclerView mLiveChannelView;
    public LiveChannelGroupNewAdapter liveChannelGroupAdapter;
    public LiveChannelItemNewAdapter liveChannelItemAdapter;

    private LinearLayout tvRightSettingLayout;
    private TvRecyclerView mSettingGroupView;
    private TvRecyclerView mSettingItemView;
    private LiveSettingGroupAdapter liveSettingGroupAdapter;
    private LiveSettingItemAdapter liveSettingItemAdapter;
    private List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();

    public static  int currentChannelGroupIndex = 0;
    private Handler mHandler = new Handler();

    private List<LiveChannelGroup> liveChannelGroupList = new ArrayList<>();
    private int currentLiveChannelIndex = -1;
    private LiveChannelItem currentLiveChannelItem = null;
    private LivePlayerManager livePlayerManager = new LivePlayerManager();
    private ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();

//EPG   by 龍
    private static LiveChannelItem  channel_Name = null;
    private static Hashtable hsEpg = new Hashtable();
    private CountDownTimer countDownTimer;
//    private CountDownTimer countDownTimerRightTop;
    TextView tv_channelnum;
    TextView tip_chname;

    TextView tv_srcinfo;
    public String epgStringAddress ="";

    private boolean isSHIYI = false;
    private boolean isBack = false;
    public static String playUrl;
    //kenson
    private ImageView imgLiveIcon;
    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
    private CountDownTimer countDownTimer3;
    private int videoWidth = 1920;
    private int videoHeight = 1080;
    private  boolean show = false;
    private PlayerTitleView mPlayerTitleView;
    private BasePopupView mSettingRightDialog;
    private BasePopupView mSettingBottomDialog;
    private BasePopupView mAllChannelRightDialog;
    private LiveStore liveStore;
    private String liveScope = "";
    private String searchQuery = "";
    private int browserMode;
    private List<LiveCatalog.Channel> displayedChannels = new ArrayList<>();
    private final List<View> browserBars = new ArrayList<>();
    private final List<RecyclerView> browserGroupViews = new ArrayList<>();
    private final LiveRetry lineRetry = new LiveRetry();
    private long loadGeneration;
    private long playGeneration;
    private final Object liveRequestTag = new Object();
    private Runnable pendingRetry;
    private AlertDialog playbackFailureDialog;
    private boolean automaticLineChange;
    private boolean failedPlayback;
    private boolean recordedPlayback;
    private boolean activityResumed;
    private boolean startWhenResumed;
    private boolean retryWhenResumed;
    private String pendingSubscriptionName;
    private LiveStore.Subscription requestedSubscription;
    private static final int IMPORT_LIVE = 9210;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live;
    }

    @Override
    protected void init() {
        ImmersionBar.with(this)
                .statusBarColor(R.color.black)
                .statusBarDarkFont(false)
                .navigationBarColor(R.color.black)
                .fitsSystemWindows(true)
                .hideBar(BarHide.FLAG_SHOW_BAR)
                .init();
        context = this;
        epgStringAddress = Hawk.get(HawkConfig.EPG_URL,"");
        if(epgStringAddress == null || epgStringAddress.length()<5)
            epgStringAddress = "http://epg.51zmt.top:8000/api/diyp/";

        findViewById(R.id.live_root).requestFocus();
        initBottomNavigation();
        mVideoView = findViewById(R.id.mVideoView);

        tvLeftChannelListLayout = findViewById(R.id.tvLeftChannnelListLayout);

        mChannelGroupView = findViewById(R.id.mGroupGridView);
        mLiveChannelView = findViewById(R.id.mChannelGridView);
        mChannelGroupView.addItemDecoration(new LinearSpacingItemDecoration(20,true));
        mLiveChannelView.addItemDecoration(new LinearSpacingItemDecoration(20,true));

        tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout);
        mSettingGroupView = findViewById(R.id.mSettingGroupView);
        mSettingItemView = findViewById(R.id.mSettingItemView);
        tvChannelInfo = findViewById(R.id.tvChannel);

        //EPG  findViewById  by 龍
        tip_chname = findViewById(R.id.tv_channel_bar_name);//底部名称
        tip_chname.setOnClickListener(view -> {
            mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
            refreshChannelBrowser();
        });
        tv_channelnum = findViewById(R.id.tv_channel_bottom_number); //底部数字

        tv_srcinfo = findViewById(R.id.tv_source);//线路状态
        tip_chname.setText("请选择频道");
        tv_srcinfo.setText("");
        updateLineControls();

        //源切换
        findViewById(R.id.ic_pre_source).setOnClickListener(view -> playPreSource());
        findViewById(R.id.ic_next_source).setOnClickListener(view -> playNextSource());
        tv_srcinfo.setOnClickListener(view -> {
            if (failedPlayback) { retryCurrentChannel(); } else { playNextSource(); }
        });
        //投屏/设置
        findViewById(R.id.ic_setting).setOnClickListener(view -> showSettingDialog(false));
        findViewById(R.id.ic_cast).setVisibility(View.GONE);

        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initSettingGroupView();
        initSettingItemView();
        initLiveSettingGroupList();
        liveStore = LiveStore.get();
        bindLiveBrowser(findViewById(R.id.live_browser), mChannelGroupView);
        initLiveChannelList();
    }

    private void initBottomNavigation() {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_nav);
        bottomNavigation.setSelectedItemId(R.id.navigation_live);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home || itemId == R.id.navigation_dashboard) {
                openMainDestination(itemId);
                return false;
            }
            if (itemId == R.id.navigation_subscription) {
                startActivity(new Intent(this, SubscriptionActivity.class));
                finish();
                return false;
            }
            return itemId == R.id.navigation_live;
        });
    }

    private void openMainDestination(int destination) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_START_DESTINATION, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showBottomEpg() {
        if (isSHIYI || currentLiveChannelItem == null) {
            return;
        }
        mPlayerTitleView.setTitle(currentLiveChannelItem.getChannelName());
        tip_chname.setText(currentLiveChannelItem.getChannelName());
        tv_channelnum.setText(String.valueOf(currentLiveChannelItem.getChannelNum()));
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        updateLineControls();
    }

    private void updateLineControls() {
        int count = currentLiveChannelItem == null ? 0 : currentLiveChannelItem.getSourceNum();
        boolean multiple = count > 1;
        findViewById(R.id.ic_pre_source).setVisibility(multiple ? View.VISIBLE : View.GONE);
        findViewById(R.id.ic_next_source).setVisibility(multiple ? View.VISIBLE : View.GONE);
        tv_srcinfo.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        tv_srcinfo.setEnabled(failedPlayback || multiple);
        tv_srcinfo.setMinHeight(ConvertUtils.dp2px(failedPlayback || multiple ? 48 : 32));
        tv_srcinfo.setTextColor(getResources().getColor(failedPlayback ? R.color.colorPrimary : R.color.text_sub_foreground));
        if (failedPlayback) {
            tv_srcinfo.setText(R.string.live_playback_retry);
        } else if (multiple) {
            tv_srcinfo.setText(getString(R.string.live_line_count, currentLiveChannelItem.getSourceIndex() + 1, count));
        } else {
            tv_srcinfo.setText(R.string.live_single_line);
        }
    }


    @Override
    public void onBackPressed() {
        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        } else if(isBack){
            isBack= false;
            playPreSource();
        } else if(mSettingBottomDialog!=null && mSettingBottomDialog.isShow()){//适配底部导航栏(手势条闪屏)变成view模式后在back时手动隐藏
            mSettingBottomDialog.dismiss();
        } else if(mSettingRightDialog!=null && mSettingRightDialog.isShow()){
            mSettingRightDialog.dismiss();
        }  else if(mAllChannelRightDialog!=null && mAllChannelRightDialog.isShow()){
            mAllChannelRightDialog.dismiss();
        } else if (!mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                //showSettingGroup();
            } else if (!isListOrSettingLayoutVisible()) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                            playNext();
                        else
                            playPrevious();
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                            playPrevious();
                        else
                            playNext();
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if(isBack){

                        }else{
                            //showSettingGroup();
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if(isBack){

                        }else{
                            playNextSource();
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        showChannelList();
                        break;
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (mVideoView != null && !failedPlayback) {
            if (startWhenResumed) {
                startWhenResumed = false;
                mVideoView.start();
            } else {
                mVideoView.resume();
            }
        }
        if (retryWhenResumed) {
            retryWhenResumed = false;
            scheduleLineRetry(2000);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;
        if (pendingRetry != null) { retryWhenResumed = true; }
        cancelLineRetry();
        if (mVideoView != null) {
            mVideoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        loadGeneration++;
        playGeneration++;
        OkGo.getInstance().cancelTag(liveRequestTag);
        if (playbackFailureDialog != null) {
            playbackFailureDialog.dismiss();
            playbackFailureDialog = null;
        }
        browserBars.clear();
        browserGroupViews.clear();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
    }

    private void showChannelList() {
        refreshChannelBrowser();
    }

    private void showChannelInfo() {
        tvChannelInfo.setText(String.format(Locale.getDefault(), "%d %s %s(%d/%d)", currentLiveChannelItem.getChannelNum(),
                currentLiveChannelItem.getChannelName(), currentLiveChannelItem.getSourceName(),
                currentLiveChannelItem.getSourceIndex() + 1, currentLiveChannelItem.getSourceNum()));

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvChannelInfo.getLayoutParams();
        params.topMargin = ConvertUtils.dp2px(10);
        params.rightMargin = ConvertUtils.dp2px(10);
        tvChannelInfo.setLayoutParams(params);

        tvChannelInfo.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideChannelInfoRun);
        mHandler.postDelayed(mHideChannelInfoRun, 3000);
    }

    private Runnable mHideChannelInfoRun = new Runnable() {
        @Override
        public void run() {
            tvChannelInfo.setVisibility(View.INVISIBLE);
        }
    };

    private boolean playChannel(int channelGroupIndex, int liveChannelIndex, boolean changeSource) {
        if (channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex && !changeSource && !failedPlayback) {
           // showChannelInfo();
            return true;
        }
        cancelLineRetry();
        playGeneration++;
        if (playbackFailureDialog != null) {
            playbackFailureDialog.dismiss();
            playbackFailureDialog = null;
        }
        mVideoView.release();
        failedPlayback = false;
        recordedPlayback = false;
        retryWhenResumed = false;
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex;
            currentLiveChannelIndex = liveChannelIndex;
            currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
        }

        if (!automaticLineChange) {
            lineRetry.begin(currentLiveChannelItem.getSourceNum(), currentLiveChannelItem.getSourceIndex());
        }
        channel_Name = currentLiveChannelItem;
        isSHIYI=false;
        isBack = false;
        if(currentLiveChannelItem.getUrl().indexOf("PLTV/8888") !=-1){
            currentLiveChannelItem.setinclude_back(true);
        }else {
            currentLiveChannelItem.setinclude_back(false);
        }
        showBottomEpg();

        mVideoView.setUrl(currentLiveChannelItem.getUrl());
       // showChannelInfo();
        if (activityResumed) {
            mVideoView.start();
        } else {
            startWhenResumed = true;
        }
        refreshChannelBrowser();
        return true;
    }

    private void playNext() {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }

    private void playPrevious() {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(-1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }

    public void playPreSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.preSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    public void playNextSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.nextSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    //显示设置列表
    private void showSettingGroup() {

        if (tvRightSettingLayout.getVisibility() == View.INVISIBLE) {
            if (!isCurrentLiveChannelValid()) return;
            //重新载入默认状态
            loadCurrentSourceList();
            liveSettingGroupAdapter.setNewData(liveSettingGroupList);
            selectSettingGroup(0, false);
            mSettingGroupView.smoothScrollToPosition(0);
            mSettingItemView.smoothScrollToPosition(currentLiveChannelItem.getSourceIndex());
            mHandler.postDelayed(mFocusAndShowSettingGroup, 200);
        } else {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        }
    }

    private Runnable mFocusAndShowSettingGroup = new Runnable() {
        @Override
        public void run() {
            if (mSettingGroupView.isScrolling() || mSettingItemView.isScrolling() || mSettingGroupView.isComputingLayout() || mSettingItemView.isComputingLayout()) {
                mHandler.postDelayed(this, 100);
            } else {
                RecyclerView.ViewHolder holder = mSettingGroupView.findViewHolderForAdapterPosition(0);
                if (holder != null)
                    holder.itemView.requestFocus();
                tvRightSettingLayout.setVisibility(View.VISIBLE);
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
                if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
                    ViewObj viewObj = new ViewObj(tvRightSettingLayout, params);
                    ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginRight", new IntEvaluator(), -tvRightSettingLayout.getLayoutParams().width, 0);
                    animator.setDuration(200);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mHandler.postDelayed(mHideSettingLayoutRun, 5000);
                        }
                    });
                    animator.start();
                }
            }
        }
    };

    private Runnable mHideSettingLayoutRun = new Runnable() {
        @Override
        public void run() {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
            if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
                ViewObj viewObj = new ViewObj(tvRightSettingLayout, params);
                ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginRight", new IntEvaluator(), 0, -tvRightSettingLayout.getLayoutParams().width);
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        tvRightSettingLayout.setVisibility(View.INVISIBLE);
                        liveSettingGroupAdapter.setSelectedGroupIndex(-1);
                    }
                });
                animator.start();
            }
        }
    };

//    private void initVideoView() {
//        StandardVideoController controller = new StandardVideoController(this);
//        controller.addControlComponent(new LiveControlView(this)); //直播控制条
//        controller.setEnableInNormal(true);
//        controller.setGestureEnabled(true);
//        mVideoView.setVideoController(controller);
//        mVideoView.setProgressManager(null);
//    }

    private void initVideoView() {
        LiveNewController controller = new LiveNewController(this);
        PlayerMenuView playerMenuView = getPlayerMenuView();
        controller.addControlComponent(playerMenuView); //菜单栏,设置投屏等
        controller.addControlComponent(new LiveControlView(this)); //直播控制条
        //标题栏
        mPlayerTitleView = new PlayerTitleView(this);
        controller.addControlComponent(mPlayerTitleView);
        controller.setListener(new LiveNewController.LiveControlListener() {

            @Override
            public void setting() {
                //showSettingGroup();
            }

            @Override
            public void playStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_IDLE:
                    case VideoView.STATE_PAUSED:
                        break;
                    case VideoView.STATE_PREPARED:
                        break;
                    case VideoView.STATE_BUFFERED:
                        if (recordedPlayback) { cancelLineRetry(); }
                        break;
                    case VideoView.STATE_PLAYING:
                        if (!activityResumed) {
                            mVideoView.pause();
                            return;
                        }
                        retryWhenResumed = false;
                        cancelLineRetry();
                        lineRetry.played();
                        if (!recordedPlayback && currentLiveChannelItem != null) {
                            recordedPlayback = true;
                            String key = currentChannelKey();
                            if (!LiveCatalog.filter(availableChannels(), "", Arrays.asList(key)).isEmpty()) {
                                liveStore.recordPlayed(liveScope, key);
                                liveStore.save();
                            }
                            refreshChannelBrowser();
                        }
                        break;
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        scheduleLineRetry(2000);
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        scheduleLineRetry((Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5000);
                        break;
                }
            }

            @Override
            public void changeSource(int direction) {
                if (direction > 0){
                    playNextSource();
                } else {
                    playPreSource();
                }
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        mVideoView.setVideoController(controller);
        mVideoView.setProgressManager(null);
    }

    @NonNull
    private PlayerMenuView getPlayerMenuView() {
        PlayerMenuView playerMenuView = new PlayerMenuView(this);
        playerMenuView.setOnPlayerMenuClickListener(new PlayerMenuView.OnPlayerMenuClickListener() {
            @Override
            public void expand() {
                showAllChannelDialog();
            }

            @Override
            public void onSetting() {
                showSettingDialog(true);
            }

        });
        return playerMenuView;
    }

    private void cancelLineRetry() {
        if (pendingRetry != null) {
            mHandler.removeCallbacks(pendingRetry);
            pendingRetry = null;
        }
    }

    private void scheduleLineRetry(long delay) {
        if (currentLiveChannelItem == null || failedPlayback) {
            return;
        }
        if (!activityResumed) {
            retryWhenResumed = true;
            return;
        }
        cancelLineRetry();
        long attempt = playGeneration;
        pendingRetry = () -> {
            if (attempt != playGeneration || !activityResumed || isFinishing() || isDestroyed()) {
                return;
            }
            int next = lineRetry.next();
            if (next < 0) {
                failedPlayback = true;
                mVideoView.release();
                updateLineControls();
                playbackFailureDialog = new AlertDialog.Builder(this).setTitle("当前频道播放失败")
                        .setMessage("已尝试此频道的全部线路，未自动换台。")
                        .setPositiveButton("重试", (dialog, which) -> {
                            if (attempt == playGeneration) { retryCurrentChannel(); }
                        })
                        .setNeutralButton("选择线路", (dialog, which) -> {
                            if (attempt == playGeneration) { showSettingDialog(false); }
                        })
                        .setNegativeButton("关闭", null).show();
                return;
            }
            currentLiveChannelItem.setSourceIndex(next);
            automaticLineChange = true;
            playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
            automaticLineChange = false;
        };
        mHandler.postDelayed(pendingRetry, delay);
    }

    private void retryCurrentChannel() {
        if (isCurrentLiveChannelValid()) {
            playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
        }
    }

    private void initChannelGroupView() {
        mChannelGroupView.setHasFixedSize(true);
        mChannelGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveChannelGroupAdapter = new LiveChannelGroupNewAdapter();
        mChannelGroupView.setAdapter(liveChannelGroupAdapter);

        //手机/模拟器
        liveChannelGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectChannelGroup(position, false, -1);
            }
        });
    }

    private void selectChannelGroup(int groupIndex, boolean focus, int liveChannelIndex) {
        if (focus) {
            liveChannelGroupAdapter.setFocusedGroupIndex(groupIndex);
        }
        if ((groupIndex > -1 && groupIndex != liveChannelGroupAdapter.getSelectedGroupIndex()) || isNeedInputPassword(groupIndex)) {
            liveChannelGroupAdapter.setSelectedGroupIndex(groupIndex);
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex);
                return;
            }
            loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
        }
    }

    private void initLiveChannelView() {
        mLiveChannelView.setHasFixedSize(true);
        mLiveChannelView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveChannelItemAdapter = new LiveChannelItemNewAdapter();
        mLiveChannelView.setAdapter(liveChannelItemAdapter);
        liveChannelItemAdapter.setOnItemChildClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            if (position >= 0 && position < displayedChannels.size()) {
                liveStore.toggleFavorite(liveScope, displayedChannels.get(position).key);
                liveStore.save();
                refreshChannelBrowser();
            }
        });

        liveChannelItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickLiveChannel(position);
            }
        });
    }

    private void clickLiveChannel(int position) {
        if (position < 0 || position >= displayedChannels.size()) {
            return;
        }
        LiveCatalog.Channel channel = displayedChannels.get(position);
        if (isNeedInputPassword(channel.groupIndex)) {
            showPasswordDialog(channel.groupIndex, channel.channelIndex);
            return;
        }
        playChannel(channel.groupIndex, channel.channelIndex, false);
    }

    private void initSettingGroupView() {
        mSettingGroupView.setHasFixedSize(true);
        mSettingGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveSettingGroupAdapter = new LiveSettingGroupAdapter();
        mSettingGroupView.setAdapter(liveSettingGroupAdapter);
        mSettingGroupView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, 5000);
            }
        });

        liveSettingGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectSettingGroup(position, false);
            }
        });
    }

    private void selectSettingGroup(int position, boolean focus) {
        if (!isCurrentLiveChannelValid()) return;
        if (focus) {
            liveSettingGroupAdapter.setFocusedGroupIndex(position);
            liveSettingItemAdapter.setFocusedItemIndex(-1);
        }
        if (position == liveSettingGroupAdapter.getSelectedGroupIndex() || position < -1)
            return;

        liveSettingGroupAdapter.setSelectedGroupIndex(position);
        liveSettingItemAdapter.setNewData(liveSettingGroupList.get(position).getLiveSettingItems());

        switch (position) {
            case 0:
                liveSettingItemAdapter.selectItem(currentLiveChannelItem.getSourceIndex(), true, false);
                break;
            case 1:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerScale(), true, true);
                break;
            case 2:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerType(), true, true);
                break;
        }
        int smoothScrollToPosition = liveSettingItemAdapter.getSelectedItemIndex();
        if (smoothScrollToPosition < 0) smoothScrollToPosition = 0;
        mSettingItemView.smoothScrollToPosition(smoothScrollToPosition);
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, 5000);
    }

    private void initSettingItemView() {
        mSettingItemView.setHasFixedSize(true);
        mSettingItemView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveSettingItemAdapter = new LiveSettingItemAdapter();
        mSettingItemView.setAdapter(liveSettingItemAdapter);
        mSettingItemView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, 5000);
            }
        });

        liveSettingItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickSettingItem(position);
            }
        });
    }

    private void clickSettingItem(int position) {
        int settingGroupIndex = liveSettingGroupAdapter.getSelectedGroupIndex();
        if (settingGroupIndex < 4) {
            if (position == liveSettingItemAdapter.getSelectedItemIndex())
                return;
            liveSettingItemAdapter.selectItem(position, true, true);
        }
        switch (settingGroupIndex) {
            case 0://线路切换
                currentLiveChannelItem.setSourceIndex(position);
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex,true);
                break;
            case 1://画面比例
                livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
                break;
            case 2://播放解码
                mVideoView.release();
                livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
                mVideoView.setUrl(currentLiveChannelItem.getUrl());
                mVideoView.start();
                break;
            case 3://超时换源
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                break;
            case 4://超时换源
                boolean select = false;
                switch (position) {
                    case 0:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select);
                        break;
                    case 1:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                        break;
                    case 2:
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                        break;
                    case 3:
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select);
                        break;
                }
                liveSettingItemAdapter.selectItem(position, select, false);
                break;
        }
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, 5000);
    }

    private void initLiveChannelList() {
        loadLiveSubscription(liveStore.current());
    }

    private void loadLiveSubscription(LiveStore.Subscription subscription) {
        long generation = ++loadGeneration;
        OkGo.getInstance().cancelTag(liveRequestTag);
        requestedSubscription = subscription;
        pendingSubscriptionName = subscription.name;
        updateBrowserBars();
        if (subscription.url.isEmpty()) {
            List<LiveChannelGroup> following = ApiConfig.get().getFollowingLiveGroups();
            if (following.size() == 1 && following.get(0).getGroupName().startsWith("http://127.0.0.1")) {
                loadLiveUrl(following.get(0).getGroupName(), subscription, generation);
            } else {
                publishLiveGroups(following, subscription, generation);
            }
        } else {
            loadLiveUrl(subscription.url, subscription, generation);
        }
    }

    private boolean currentLoad(long generation) {
        return generation == loadGeneration && !isFinishing() && !isDestroyed();
    }

    private void loadLiveUrl(String address, LiveStore.Subscription subscription, long generation) {
        String url = address;
        try {
            Uri uri = Uri.parse(url);
            if ("127.0.0.1".equals(uri.getHost()) && uri.getQueryParameter("ext") != null) {
                url = new String(Base64.decode(uri.getQueryParameter("ext"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
            }
        } catch (Exception error) {
            liveLoadFailed(generation);
            return;
        }
        if (url.startsWith("content://")) {
            String localUrl = url;
            new Thread(() -> {
                try (InputStream input = getContentResolver().openInputStream(Uri.parse(localUrl));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) { content.append(line).append('\n'); }
                    runOnUiThread(() -> parseLiveCandidate(content.toString(), subscription, generation));
                } catch (Exception error) {
                    runOnUiThread(() -> liveLoadFailed(generation));
                }
            }).start();
            return;
        }
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            liveLoadFailed(generation);
            return;
        }
        OkGo.<String>get(url).tag(liveRequestTag).execute(new AbsCallback<String>() {
            @Override public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }
            @Override public void onSuccess(Response<String> response) {
                parseLiveCandidate(response.body(), subscription, generation);
            }
            @Override public void onError(Response<String> response) {
                liveLoadFailed(generation);
            }
        });
    }

    private void parseLiveCandidate(String content, LiveStore.Subscription subscription, long generation) {
        if (!currentLoad(generation)) { return; }
        LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> parsed = new LinkedHashMap<>();
        TxtSubscribe.parse(parsed, content);
        try {
            publishLiveGroups(ApiConfig.parseLiveGroups(TxtSubscribe.live2JsonArray(parsed)), subscription, generation);
        } catch (Exception error) {
            liveLoadFailed(generation);
        }
    }

    private void publishLiveGroups(List<LiveChannelGroup> groups, LiveStore.Subscription subscription, long generation) {
        if (!currentLoad(generation)) { return; }
        boolean hasChannel = false;
        for (LiveChannelGroup group : groups) {
            if (group.getLiveChannels() != null && !group.getLiveChannels().isEmpty()) { hasChannel = true; }
        }
        if (!hasChannel) { liveLoadFailed(generation); return; }
        cancelLineRetry();
        playGeneration++;
        mVideoView.release();
        if (playbackFailureDialog != null) {
            playbackFailureDialog.dismiss();
            playbackFailureDialog = null;
        }
        startWhenResumed = false;
        retryWhenResumed = false;
        tip_chname.setText("请选择频道");
        tv_srcinfo.setText("");
        currentLiveChannelItem = null;
        updateLineControls();
        currentLiveChannelIndex = -1;
        currentChannelGroupIndex = -1;
        channelGroupPasswordConfirmed.clear();
        liveStore.select(subscription.id);
        Hawk.put(HawkConfig.LIVE_URL, subscription.url);
        liveStore.save();
        liveScope = liveStore.scope(Hawk.get(HawkConfig.API_URL, ""));
        ApiConfig.get().replaceLiveGroups(groups);
        liveChannelGroupList = new ArrayList<>(groups);
        pendingSubscriptionName = null;
        showSuccess();
        initLiveState();
    }

    private void liveLoadFailed(long generation) {
        if (!currentLoad(generation)) { return; }
        pendingSubscriptionName = null;
        updateBrowserBars();
        new AlertDialog.Builder(this).setTitle("直播订阅加载失败")
                .setMessage(liveChannelGroupList.isEmpty() ? "未获取到可用频道，请重试或选择其他直播订阅。" : "仍使用原直播订阅，原频道和观看记录未改变。")
                .setPositiveButton("选择订阅", (dialog, which) -> showLiveSubscriptions())
                .setNeutralButton("重试", (dialog, which) -> loadLiveSubscription(requestedSubscription))
                .setNegativeButton("关闭", null).show();
    }

    private void initLiveState() {
        livePlayerManager.init(mVideoView);
        tvRightSettingLayout.setVisibility(View.INVISIBLE);
        liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        List<LiveCatalog.Channel> restored = LiveCatalog.filter(availableChannels(), "", Arrays.asList(liveStore.last(liveScope)));
        int group = getFirstNoPasswordChannelGroup();
        int channel = 0;
        if (!restored.isEmpty()) {
            group = restored.get(0).groupIndex;
            channel = restored.get(0).channelIndex;
        }
        if (group >= 0) {
            liveChannelGroupAdapter.setSelectedGroupIndex(group);
            playChannel(group, channel, false);
        }
        refreshChannelBrowser();
    }

    private boolean isListOrSettingLayoutVisible() {
        return tvLeftChannelListLayout.getVisibility() == View.VISIBLE || tvRightSettingLayout.getVisibility() == View.VISIBLE;
    }

    private void initLiveSettingGroupList() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置"));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        ArrayList<String> sourceItems = new ArrayList<>();
        ArrayList<String> scaleItems = new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪"));
        ArrayList<String> playerDecoderItems = new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo"));
        ArrayList<String> timeoutItems = new ArrayList<>(Arrays.asList("5s", "10s", "15s", "20s", "25s", "30s"));
        ArrayList<String> personalSettingItems = new ArrayList<>(Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类"));
        itemsArrayList.add(sourceItems);
        itemsArrayList.add(scaleItems);
        itemsArrayList.add(playerDecoderItems);
        itemsArrayList.add(timeoutItems);
        itemsArrayList.add(personalSettingItems);

        liveSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            liveSettingGroupList.add(liveSettingGroup);
        }
        liveSettingGroupList.get(3).getLiveSettingItems().get(Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)).setItemSelected(true);
        liveSettingGroupList.get(4).getLiveSettingItems().get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
    }

    private void loadCurrentSourceList() {
        ArrayList<String> currentSourceNames = currentLiveChannelItem.getChannelSourceNames();
        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
        for (int j = 0; j < currentSourceNames.size(); j++) {
            LiveSettingItem liveSettingItem = new LiveSettingItem();
            liveSettingItem.setItemIndex(j);
            liveSettingItem.setItemName(currentSourceNames.get(j));
            liveSettingItemList.add(liveSettingItem);
        }
        liveSettingGroupList.get(0).setLiveSettingItems(liveSettingItemList);
    }

    private void showPasswordDialog(int groupIndex, int liveChannelIndex) {
        long generation = loadGeneration;
        LivePasswordDialog dialog = new LivePasswordDialog(this);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if (!currentLoad(generation)) { return; }
                if (password.equals(liveChannelGroupList.get(groupIndex).getGroupPassword())) {
                    channelGroupPasswordConfirmed.add(groupIndex);
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
                } else {
                    Toast.makeText(App.getInstance(), "密码错误", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancel() {
                if (!currentLoad(generation)) { return; }
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    int groupIndex = liveChannelGroupAdapter.getSelectedGroupIndex();
                    refreshChannelBrowser();
                }
            }
        });
        dialog.show();
    }

    private void loadChannelGroupDataAndPlay(int groupIndex, int liveChannelIndex) {
        refreshChannelBrowser();
        if (liveChannelIndex >= 0) {
            playChannel(groupIndex, liveChannelIndex, false);
        }
    }

    private boolean isNeedInputPassword(int groupIndex) {
        return !liveChannelGroupList.get(groupIndex).getGroupPassword().isEmpty()
                && !isPasswordConfirmed(groupIndex);
    }

    private boolean isPasswordConfirmed(int groupIndex) {
        for (Integer confirmedNum : channelGroupPasswordConfirmed) {
            if (confirmedNum == groupIndex)
                return true;
        }
        return false;
    }

    private ArrayList<LiveChannelItem> getLiveChannels(int groupIndex) {
        if (!isNeedInputPassword(groupIndex)) {
            return liveChannelGroupList.get(groupIndex).getLiveChannels();
        } else {
            return new ArrayList<>();
        }
    }

    private Integer[] getNextChannel(int direction) {
        int group = currentChannelGroupIndex;
        int channel = currentLiveChannelIndex + direction;
        if (channel >= 0 && channel < getLiveChannels(group).size()) {
            return new Integer[]{group, channel};
        }
        if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
            for (int checked = 0; checked < liveChannelGroupList.size(); checked++) {
                group = (group + direction + liveChannelGroupList.size()) % liveChannelGroupList.size();
                if (!isNeedInputPassword(group) && !getLiveChannels(group).isEmpty()) {
                    return new Integer[]{group, direction > 0 ? 0 : getLiveChannels(group).size() - 1};
                }
            }
        }
        return new Integer[]{currentChannelGroupIndex, direction > 0 ? 0 : getLiveChannels(currentChannelGroupIndex).size() - 1};
    }

    private int getFirstNoPasswordChannelGroup() {
        for (LiveChannelGroup liveChannelGroup : liveChannelGroupList) {
            if (liveChannelGroup.getGroupPassword().isEmpty() && !liveChannelGroup.getLiveChannels().isEmpty()) {
                return liveChannelGroup.getGroupIndex();
            }
        }
        return -1;
    }

    private boolean isCurrentLiveChannelValid() {
        if (currentLiveChannelItem == null) {
            Toast.makeText(App.getInstance(), "请先选择频道", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    //计算两个时间相差的秒数
    public static long getTime(String startTime, String endTime)  {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long eTime = 0;
        try {
            eTime = df.parse(endTime).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long sTime = 0;
        try {
            sTime = df.parse(startTime).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long diff = (eTime - sTime) / 1000;
        return diff;
    }
    private  String durationToString(int duration) {
        String result = "";
        int dur = duration / 1000;
        int hour=dur/3600;
        int min = (dur / 60) % 60;
        int sec = dur % 60;
        if(hour>0){
            if (min > 9) {
                if (sec > 9) {
                    result =hour+":"+ min + ":" + sec;
                } else {
                    result =hour+":"+ min + ":0" + sec;
                }
            } else {
                if (sec > 9) {
                    result =hour+":"+ "0" + min + ":" + sec;
                } else {
                    result = hour+":"+"0" + min + ":0" + sec;
                }
            }
        }else{
            if (min > 9) {
                if (sec > 9) {
                    result = min + ":" + sec;
                } else {
                    result = min + ":0" + sec;
                }
            } else {
                if (sec > 9) {
                    result ="0" + min + ":" + sec;
                } else {
                    result = "0" + min + ":0" + sec;
                }
            }
        }
        return result;
    }

    private boolean updatingBrowserBars;

    private List<LiveCatalog.Channel> availableChannels() {
        return LiveCatalog.channels(liveChannelGroupList, new HashSet<>(channelGroupPasswordConfirmed));
    }

    private String currentChannelKey() {
        if (currentLiveChannelItem == null || currentChannelGroupIndex < 0) { return ""; }
        return LiveCatalog.key(liveChannelGroupList.get(currentChannelGroupIndex).getGroupName(), currentLiveChannelItem.getChannelName());
    }

    public void bindLiveBrowser(View bar, RecyclerView groups) {
        browserBars.add(bar);
        browserGroupViews.add(groups);
        EditText search = bar.findViewById(R.id.live_search);
        search.setText(searchQuery);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (!updatingBrowserBars) {
                    searchQuery = text.toString();
                    refreshChannelBrowser();
                }
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        bar.findViewById(R.id.live_clear).setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            searchQuery = "";
            refreshChannelBrowser();
        });
        int[] modes = {R.id.live_all, R.id.live_favorites, R.id.live_recent};
        for (int i = 0; i < modes.length; i++) {
            final int mode = i;
            bar.findViewById(modes[i]).setOnClickListener(view -> {
                FastClickCheckUtil.check(view);
                browserMode = mode;
                refreshChannelBrowser();
            });
        }
        bar.findViewById(R.id.live_subscription).setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            showLiveSubscriptions();
        });
        bar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                browserBars.remove(bar);
                browserGroupViews.remove(groups);
            }
        });
        refreshChannelBrowser();
    }

    private void refreshChannelBrowser() {
        if (liveStore == null || liveChannelItemAdapter == null) { return; }
        List<String> keys = browserMode == 1 ? liveStore.favorites(liveScope)
                : browserMode == 2 ? liveStore.recent(liveScope) : null;
        List<LiveCatalog.Channel> channels = LiveCatalog.filter(availableChannels(), searchQuery, keys);
        if (browserMode == 0 && searchQuery.trim().isEmpty()) {
            int selectedGroup = liveChannelGroupAdapter.getSelectedGroupIndex();
            List<LiveCatalog.Channel> selected = new ArrayList<>();
            for (LiveCatalog.Channel channel : channels) {
                if (channel.groupIndex == selectedGroup) { selected.add(channel); }
            }
            channels = selected;
        }
        displayedChannels = channels;
        liveChannelItemAdapter.showChannels(channels, liveStore.favorites(liveScope), currentLiveChannelItem);
        updateBrowserBars();
    }

    private void updateBrowserBars() {
        if (liveStore == null) { return; }
        updatingBrowserBars = true;
        for (View bar : browserBars) {
            TextView source = bar.findViewById(R.id.live_subscription);
            source.setText(pendingSubscriptionName == null ? liveStore.current().name + " · 切换 / 管理" : "正在加载 " + pendingSubscriptionName);
            EditText search = bar.findViewById(R.id.live_search);
            if (!search.getText().toString().equals(searchQuery)) { search.setText(searchQuery); }
            int[] modes = {R.id.live_all, R.id.live_favorites, R.id.live_recent};
            for (int i = 0; i < modes.length; i++) {
                TextView mode = bar.findViewById(modes[i]);
                mode.setTextColor(getResources().getColor(browserMode == i ? R.color.colorPrimary : R.color.text_foreground));
                mode.setSelected(browserMode == i);
            }
            TextView empty = bar.findViewById(R.id.live_empty);
            empty.setVisibility(displayedChannels.isEmpty() ? View.VISIBLE : View.GONE);
            empty.setText(!searchQuery.trim().isEmpty() ? "没有匹配的频道" : browserMode == 1 ? "暂无收藏，点击频道旁的收藏按钮添加" : browserMode == 2 ? "暂无成功播放记录" : "此分类暂无可见频道");
        }
        for (RecyclerView groups : browserGroupViews) {
            groups.setVisibility(browserMode == 0 && searchQuery.trim().isEmpty() ? View.VISIBLE : View.GONE);
        }
        updatingBrowserBars = false;
    }

    private void showLiveSubscriptions() {
        List<LiveStore.Subscription> subscriptions = liveStore.subscriptions();
        List<String> entries = new ArrayList<>();
        for (LiveStore.Subscription subscription : subscriptions) {
            entries.add(subscription.name + (subscription.id.equals(liveStore.current().id) ? "（当前）" : ""));
        }
        final int subscriptionCount = entries.size();
        entries.add("———— 管理 ————");
        entries.add("添加直播地址");
        entries.add("本地导入文件");
        entries.add("重命名当前订阅");
        entries.add("删除订阅");
        new AlertDialog.Builder(this).setTitle("直播订阅")
                .setItems(entries.toArray(new String[0]), (dialog, which) -> {
                    if (which < subscriptionCount) {
                        loadLiveSubscription(subscriptions.get(which));
                        return;
                    }
                    switch (which - subscriptionCount) {
                        case 1:
                            addLiveSubscription();
                            break;
                        case 2:
                            importLiveSubscription();
                            break;
                        case 3:
                            renameCurrentSubscription();
                            break;
                        case 4:
                            showDeleteSubscriptions();
                            break;
                        default:
                            break;
                    }
                }).show();
    }

    private void importLiveSubscription() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, IMPORT_LIVE);
    }

    private void renameCurrentSubscription() {
        LiveSubscriptionUi.showRenameDialog(this, liveStore, changed -> updateBrowserBars());
    }

    private void showDeleteSubscriptions() {
        LiveSubscriptionUi.showDeleteDialog(this, liveStore, removedCurrent -> {
            if (removedCurrent) {
                Hawk.put(HawkConfig.LIVE_URL, "");
                loadLiveSubscription(liveStore.current());
            } else {
                updateBrowserBars();
            }
        });
    }

    private void addLiveSubscription() {
        EditText address = new EditText(this);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("https://…");
        new AlertDialog.Builder(this).setTitle("添加直播地址").setView(address)
                .setPositiveButton("下一步", (dialog, which) -> {
                    String url = address.getText().toString().trim();
                    Uri parsed = Uri.parse(url);
                    if (!("https".equals(parsed.getScheme()) || "http".equals(parsed.getScheme())) || parsed.getHost() == null) {
                        ToastUtils.showShort("请输入有效的 HTTP 或 HTTPS 直播地址");
                        return;
                    }
                    nameLiveSubscription(url);
                }).setNegativeButton("取消", null).show();
    }

    private void nameLiveSubscription(String url) {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("例如：足球直播");
        new AlertDialog.Builder(this).setTitle("直播订阅名称").setView(name)
                .setPositiveButton("添加并加载", (dialog, which) -> {
                    LiveStore.Subscription subscription = liveStore.upsert(name.getText().toString(), url);
                    liveStore.save();
                    loadLiveSubscription(subscription);
                }).setNegativeButton("取消", null).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_LIVE || resultCode != RESULT_OK || data == null || data.getData() == null) { return; }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException error) {
            ToastUtils.showShort("无法持久保存文件授权，请重新选择文件");
            return;
        }
        nameLiveSubscription(uri.toString());
    }

    public void showAllChannelDialog() {
        mAllChannelRightDialog = new XPopup.Builder(this)
                .isViewMode(true)
                .hasNavigationBar(false)
                .hasShadowBg(false)
                .popupHeight(ScreenUtils.getScreenHeight())
                .popupWidth(Math.min(ScreenUtils.getScreenWidth() * 3 / 4, ConvertUtils.dp2px(480)))
                .popupPosition(PopupPosition.Right)
                .asCustom(new AllChannelsRightDialog(this));
        mAllChannelRightDialog.show();
    }

    public LivePlayerManager getLivePlayerManager(){
        return livePlayerManager;
    }

    public LiveChannelItem getCurrentLiveChannelItem(){
        return currentLiveChannelItem;
    }

    /**
     * 切换某个线路播放
     * @param position
     */
    public void switchingLine2Replay(int position){
        currentLiveChannelItem.setSourceIndex(position);
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex,true);
    }

    /**
     * 切换缩放比例
     * @param position
     */
    public void changeScale(int position){
        livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
    }

    /**
     * 更换播放解码
     * @param position
     */
    public void changePlayer(int position){
        cancelLineRetry();
        playGeneration++;
        failedPlayback = false;
        lineRetry.begin(currentLiveChannelItem.getSourceNum(), currentLiveChannelItem.getSourceIndex());
        mVideoView.release();
        livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
        mVideoView.setUrl(currentLiveChannelItem.getUrl());
        mVideoView.start();
    }

    /**
     * 设置弹窗
     * @param fullScreenStyle 全屏显示侧边弹窗
     */
    private void showSettingDialog(boolean fullScreenStyle) {
        if (!isCurrentLiveChannelValid()){
            ToastUtils.showShort("当前频道未加载");
            return;
        }
        if (fullScreenStyle){
            mSettingRightDialog = new XPopup.Builder(this)
                    .isViewMode(true)
                    .hasNavigationBar(false)
                    .hasShadowBg(false)
                    .popupHeight(ScreenUtils.getScreenHeight())
                    .popupWidth(ConvertUtils.dp2px(300))
                    .popupPosition(PopupPosition.Right)
                    .asCustom(new LiveSettingRightDialog(this));
            mSettingRightDialog.show();
        }else {
            mSettingBottomDialog = new XPopup.Builder(this)
                    .isViewMode(true)
                    .popupHeight(ScreenUtils.getScreenHeight()/2)
                    .hasNavigationBar(false)
                    .hasShadowBg(false)
                    .asCustom(new LiveSettingDialog(this));
            mSettingBottomDialog.show();
        }

    }

}
