package com.sherdle.universal.attachmentviewer.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.devbrackets.android.exomedia.listener.OnErrorListener;
import com.devbrackets.android.exomedia.listener.OnPreparedListener;
import com.devbrackets.android.exomedia.ui.listener.VideoControlsVisibilityListener;
import com.devbrackets.android.exomedia.ui.widget.VideoView;
import com.devbrackets.android.exomedia.ui.widget.controls.VideoControls;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.sherdle.universal.HolderActivity;
import com.sherdle.universal.R;

/**
 * This file is part of the Universal template
 * For license information, please check the LICENSE
 * file in the root of this project
 *
 * @author Sherdle
 * Copyright 2019
 */
public class VideoPlayerActivity extends Activity implements OnPreparedListener {

    private static final String URL = "url";
    private VideoView videoView;

    public static void startActivity(Context fromActivity, String url){
        Intent intent = new Intent(fromActivity, VideoPlayerActivity.class);
        intent.putExtra(URL, url);
        fromActivity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getExtras().getString(URL);
        setContentView(R.layout.activity_attachment_video);

        videoView = findViewById(R.id.video_view);
        videoView.setOnPreparedListener(this);

        videoView.setMedia(Uri.parse(url));

        videoView.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(Exception e) {
                if (e.getCause() instanceof UnrecognizedInputFormatException) {
                    HolderActivity.startWebViewActivity(VideoPlayerActivity.this, url, true, false, null);
                    finish();
                    return true;
                }
                return false;
            }
        });
        assert videoView.getVideoControls() != null;
    }

    @Override
    public void onPrepared() {
        videoView.start();
    }
}
