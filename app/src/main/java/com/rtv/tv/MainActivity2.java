package com.rtv.tv;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.*;

import java.util.ArrayList;
import java.util.Locale;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

public class MainActivity extends Activity {

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private RelativeLayout playerBox;
    private LinearLayout controlBar;
    private ProgressBar loader;
    private TextView btnPlayPause;
    private TextureView textureView;
    private WebView web;

    private TextView txtChannelName;
    private TextView txtQuality;
    private ImageView btnCast;

    private TextView txtCurrentTime, txtTotalTime;
    private SeekBar seekBar;

    private Handler freezeHandler = new Handler();
    private Handler hideHandler = new Handler();
    private Handler progressHandler = new Handler();
    private String currentUrl = "";
    private long lastTime = 0;
    private long savedTime = 0; 
    private int freezeCount = 0;
    private boolean isReconnecting = false;
    private boolean isFullScreen = false;
    private GestureDetector gestureDetector;
    private boolean isSeeking = false;
    private boolean isRelativeSeek = false;
    // متغير لضمان استقرار دالة التحميل ومنعها من إعادة البث أثناء الـ Pause اليدوي
    private boolean isManualPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.splash);

        new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					initAppLogic();
				}
			}, 3000);
    }

    private void initAppLogic() {
        setContentView(R.layout.main);

        ArrayList<String> options = new ArrayList<String>();
        // إعدادات محسنة لسرعة البث واستقرار النت في AIDE
        options.add("--network-caching=3000"); 
        options.add("--live-caching=1000");    
        options.add("--file-caching=2000");
        options.add("--clock-jitter=500");       
        options.add("--codec=mediacodec_ndk,mediacodec_dr,all"); 
        options.add("--avcodec-fast");
        options.add("--avcodec-skiploopfilter=1");
        options.add("--drop-late-frames");
        options.add("--skip-frames");
        options.add("--no-osd");               

        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);

        playerBox = (RelativeLayout) findViewById(R.id.player_layout);
        controlBar = (LinearLayout) findViewById(R.id.control_bar);
        loader = (ProgressBar) findViewById(R.id.loading_speed);
        btnPlayPause = (TextView) findViewById(R.id.btn_play_pause);
        textureView = (TextureView) findViewById(R.id.vlc_texture);
        web = (WebView) findViewById(R.id.webview);

        txtChannelName = (TextView) findViewById(R.id.txt_channel_name);
        txtQuality = (TextView) findViewById(R.id.txt_video_quality);
        btnCast = (ImageView) findViewById(R.id.btn_cast);

        txtCurrentTime = (TextView) findViewById(R.id.txt_current_time);
        txtTotalTime = (TextView) findViewById(R.id.txt_total_time);
        seekBar = (SeekBar) findViewById(R.id.video_seekbar);

        playerBox.setFocusable(true);
        playerBox.requestFocus();

        // ربط أزرار التحكم باللمس لتعمل في الهاتف (AIDE Compatibility)
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { togglePlayPause(null); }
			});

        View btnRew = findViewById(R.id.btn_rewind);
        if (btnRew != null) btnRew.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { seekBackward(null); }
				});

        View btnFwd = findViewById(R.id.btn_forward);
        if (btnFwd != null) btnFwd.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { seekForward(null); }
				});

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
						if (fromUser && mediaPlayer != null) {
							if (isRelativeSeek) {
								mediaPlayer.setPosition(progress / 1000f);
							} else {
								mediaPlayer.setTime(progress);
							}
							txtCurrentTime.setText(formatTime(mediaPlayer.getTime()));
						}
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
						isSeeking = true;
						hideHandler.removeCallbacksAndMessages(null);
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
						if (mediaPlayer != null) {
							if (isRelativeSeek) mediaPlayer.setPosition(seekBar.getProgress() / 1000f);
							else mediaPlayer.setTime(seekBar.getProgress());
						}
						isSeeking = false;
						if (isFullScreen) startHideTimer();
					}
				});
        }

        IVLCVout vout = mediaPlayer.getVLCVout();
        vout.setVideoView(textureView);
        vout.attachViews();

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        web.setFocusable(true);

        setupEvents();

        if (btnCast != null) {
            btnCast.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						castDLNA();
					}
				});
        }

        web.loadUrl("file:///android_asset/index.html");
    }

    private void enableImmersiveMode() {
        if (isFullScreen) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION 
                | View.SYSTEM_UI_FLAG_FULLSCREEN    
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullScreen) {
            enableImmersiveMode();
        }
    }

    private void setupEvents() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
				@Override
				public boolean onDoubleTap(MotionEvent e) {
					toggleScreenMode();
					return true;
				}

				@Override
				public boolean onSingleTapConfirmed(MotionEvent e) {
					if (isFullScreen) {
						if (controlBar.getVisibility() == View.VISIBLE) {
							controlBar.setVisibility(View.GONE);
						} else {
							controlBar.setVisibility(View.VISIBLE);
                            controlBar.bringToFront();
							startHideTimer();
						}
					}
					return true;
				}
			});

        textureView.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN && isFullScreen) {
                        controlBar.setVisibility(View.VISIBLE);
                        controlBar.bringToFront();
                        startHideTimer();
                    }
					gestureDetector.onTouchEvent(event);
					return true;
				}
			});

        mediaPlayer.setEventListener(new MediaPlayer.EventListener() {
				@Override
				public void onEvent(final MediaPlayer.Event event) {
					runOnUiThread(new Runnable() {
							@Override
							public void run() {
								if (event.type == MediaPlayer.Event.Playing) {
									loader.setVisibility(View.GONE);
									freezeCount = 0;
									isReconnecting = false;
									btnPlayPause.setText("⏸");
									startUpdater();
									applyLayout();

									if (savedTime > 0) {
										mediaPlayer.setTime(savedTime);
										savedTime = 0;
									}
								} else if (event.type == MediaPlayer.Event.Paused) {
									btnPlayPause.setText("▶");
								} else if (event.type == MediaPlayer.Event.Buffering) {
                                    if (event.getBuffering() < 100) loader.setVisibility(View.VISIBLE);
                                    else loader.setVisibility(View.GONE);
                                }
							}
						});
				}
			});

        web.addJavascriptInterface(new Object() {
				@JavascriptInterface
				public void playSide(final String url, final String name, final String quality) {
					runOnUiThread(new Runnable() {
							@Override
							public void run() {
								currentUrl = url;
								savedTime = 0; 
                                isManualPaused = false;
								if (txtChannelName != null) txtChannelName.setText(name);
								if (txtQuality != null) txtQuality.setText(quality);
								isFullScreen = false;
								applyLayout();
								startPlayer(url);
							}
						});
				}

				@JavascriptInterface
				public void toggleFullScreen() {
					runOnUiThread(new Runnable() {
							@Override
							public void run() {
								toggleScreenMode();
							}
						});
				}
			}, "Android");
    }

    private void startUpdater() {
        progressHandler.removeCallbacksAndMessages(null);
        progressHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mediaPlayer != null && !isSeeking) {
						long cur = mediaPlayer.getTime();
						long tot = mediaPlayer.getLength();
						if (txtCurrentTime != null) txtCurrentTime.setText(formatTime(cur));

						if (isRelativeSeek) {
							if (seekBar != null) {
								seekBar.setMax(1000);
								seekBar.setProgress((int) (mediaPlayer.getPosition() * 1000));
							}
							txtTotalTime.setText("∞");
						} else {
							if (tot > 0) {
								if (txtTotalTime != null) txtTotalTime.setText(formatTime(tot));
								if (seekBar != null) {
									seekBar.setMax((int) tot);
									seekBar.setProgress((int) cur);
								}
							}
						}
						progressHandler.postDelayed(this, 500);
					}
				}
			}, 500);
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long seconds = ms / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void castDLNA() {
        if (currentUrl.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(currentUrl), "video/*");
            startActivity(Intent.createChooser(intent, "إرسال البث..."));
        } catch (Exception e) {}
    }

    private void startHideTimer() {
        hideHandler.removeCallbacksAndMessages(null);
        hideHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (isFullScreen && mediaPlayer.isPlaying()) controlBar.setVisibility(View.GONE);
				}
			}, 4000);
    }

    private long lastClickTime = 0;
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isFullScreen && playerBox.getVisibility() == View.VISIBLE) {
            if (controlBar.getVisibility() != View.VISIBLE) {
                controlBar.setVisibility(View.VISIBLE);
                startHideTimer();
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!isFullScreen) return super.onKeyDown(keyCode, event);
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 300) {
                toggleScreenMode();
            } else {
                togglePlayPause(null);
                if (isFullScreen) startHideTimer();
            }
            lastClickTime = currentTime;
            return true;
        }

        if (isFullScreen) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { seekForward(null); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { seekBackward(null); return true; }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { 
                mediaPlayer.setVolume(Math.min(100, mediaPlayer.getVolume() + 5)); 
                return true; 
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { 
                mediaPlayer.setVolume(Math.max(0, mediaPlayer.getVolume() - 5)); 
                return true; 
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toggleScreenMode() {
        isFullScreen = !isFullScreen;
        applyLayout();
    }

    private void applyLayout() {
        FrameLayout.LayoutParams lp;
        if (isFullScreen) {
            lp = new FrameLayout.LayoutParams(-1, -1);
            controlBar.setVisibility(View.VISIBLE);
            controlBar.bringToFront();
            startHideTimer();
            enableImmersiveMode();
        } else {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            lp = new FrameLayout.LayoutParams((int)(screenW*0.5),(int)(screenH*0.5));
            lp.topMargin = (int)(55*getResources().getDisplayMetrics().density);
            controlBar.setVisibility(View.GONE);
            enableImmersiveMode();
        }
        playerBox.setLayoutParams(lp);
        new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mediaPlayer != null) {
						mediaPlayer.getVLCVout().setWindowSize(textureView.getWidth(), textureView.getHeight());
						mediaPlayer.setAspectRatio(textureView.getWidth() + ":" + textureView.getHeight());
					}
				}
			}, 150);
    }

    private void startPlayer(String url) {
        playerBox.setVisibility(View.VISIBLE);
        loader.setVisibility(View.VISIBLE);
        Media media = new Media(libVLC, Uri.parse(url));
        mediaPlayer.setMedia(media);
        media.release();
        mediaPlayer.play();
        btnPlayPause.setText("⏸");
        isManualPaused = false;
        startFreezeCheck();

        new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					long len = mediaPlayer.getLength();
					isRelativeSeek = (len <= 0);
				}
			}, 1500);
    }

    private void startFreezeCheck() {
        freezeHandler.removeCallbacksAndMessages(null);
        freezeHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mediaPlayer == null || isManualPaused) return;
					long t = mediaPlayer.getTime();
					boolean isLive = mediaPlayer.getLength() <= 0;
					if ((mediaPlayer.isPlaying() || isLive)) {
						if (t <= lastTime && t > 0) { 
							freezeCount++;
							loader.setVisibility(View.VISIBLE);
							if (freezeCount >= 4) { // تقليل وقت الانتظار لسرعة الاستجابة
								freezeCount = 0;
								reconnectForce();
							}
						} else {
							freezeCount = 0;
							loader.setVisibility(View.GONE);
						}
						lastTime = t;
					}
					freezeHandler.postDelayed(this, 1000);
				}
			}, 1000);
    }

    private void reconnectForce() {
        if (isReconnecting) return;
        isReconnecting = true;

        if (mediaPlayer != null && mediaPlayer.getLength() > 0) {
            savedTime = mediaPlayer.getTime();
        }

        mediaPlayer.stop();
        freezeHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					isReconnecting = false;
					startPlayer(currentUrl);
				}
			}, 2000);
    }

    @Override protected void onPause() { super.onPause(); if (mediaPlayer != null) mediaPlayer.stop(); }
    @Override protected void onStop() { super.onStop(); if (mediaPlayer != null) mediaPlayer.stop(); }
    @Override protected void onDestroy() { super.onDestroy(); if (mediaPlayer != null) { mediaPlayer.release(); libVLC.release(); } }

    public void seekForward(View v) {
        if (mediaPlayer != null) {
            long pos = mediaPlayer.getTime() + 30000;
            if (mediaPlayer.getLength() > 0 && pos > mediaPlayer.getLength()) pos = mediaPlayer.getLength();
            mediaPlayer.setTime(pos);
            startHideTimer();
        }
    }

    public void seekBackward(View v) {
        if (mediaPlayer != null) {
            long pos = mediaPlayer.getTime() - 30000;
            if (pos < 0) pos = 0;
            mediaPlayer.setTime(pos);
            startHideTimer();
        }
    }

    public void togglePlayPause(View v) {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isManualPaused = true; 
            btnPlayPause.setText("▶");
            freezeHandler.removeCallbacksAndMessages(null); 
        } else {
            isManualPaused = false; 
            mediaPlayer.play();
            btnPlayPause.setText("⏸");
            startFreezeCheck();
        }
    }

    @Override
    public void onBackPressed() {
        if (isFullScreen) {
            isFullScreen = false;
            applyLayout();
        } else if (playerBox.getVisibility() == View.VISIBLE) {
            if (mediaPlayer != null) mediaPlayer.stop();
            playerBox.setVisibility(View.GONE);
        } else {
            super.onBackPressed();
        }
    }
}
