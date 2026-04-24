/************************************************************************
 Reverse.java - Copyright (c) 2012 (: JOBnik! :) [Arthur Aminov, ISRAEL]
                                                 [http://www.jobnik.org]
                                                 [   bass_fx@jobnik.org]
 
 BASS_FX playing in reverse with tempo & dx8 fx
*************************************************************************/

package com.example.reverse;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import java.lang.Math;
import java.util.Arrays;

import com.un4seen.bass.BASS;
import com.un4seen.bass.BASS_FX;

public class Reverse extends Activity {
	int chan;	// reversed tempo handle
	int fx;		// dx8 reverb handle

	File filepath;
	String[] filelist;

	class RunnableParam implements Runnable {
		Object param;
		RunnableParam(Object p) { param=p; }
		public void run() {}
	}
	
	// display error messages
	void Error(String es) {
		// get error code in current thread for display in UI thread
		String s=String.format("%s\n(error code: %d)", es, BASS.BASS_ErrorGetCode());
		runOnUiThread(new RunnableParam(s) {
            public void run() {
        		new AlertDialog.Builder(Reverse.this)
    				.setMessage((String)param)
    				.setPositiveButton("OK", null)
    				.show();
            }
		});
	}

	// update dx8 reverb
	public void UpdateFX(int a) {
		BASS.BASS_DX8_REVERB p=new BASS.BASS_DX8_REVERB();

		BASS.BASS_FXGetParameters(fx, p);
		p.fReverbMix=(float)(a!=0?Math.log(a/20.0)*20:-96);
		BASS.BASS_FXSetParameters(fx, p);
	}

	// show the position the file at
	void UpdatePositionLabel()
	{
		if(BASS_FX.BASS_FX_TempoGetRateRatio(chan)==0) return;
		{
			float totalsec=(float)((SeekBar)findViewById(R.id.sbPosition)).getMax()/BASS_FX.BASS_FX_TempoGetRateRatio(chan);
			float posec=(float)((SeekBar)findViewById(R.id.sbPosition)).getProgress()/BASS_FX.BASS_FX_TempoGetRateRatio(chan);
			String s=String.format("Playing position: %02d:%02d / %02d:%02d", (int)posec/60,(int)posec%60,(int)totalsec/60,(int)totalsec%60);
			((TextView)findViewById(R.id.txtPos)).setText(s);
		}
	}

	public void OpenClicked(View v) {
		String[] list=filepath.list();
		if (list==null) list=new String[0];
		if (!filepath.getPath().equals("/")) {
			filelist=new String[list.length+1];
			filelist[0]="..";
			System.arraycopy(list, 0, filelist, 1, list.length);
		} else
			filelist=list;
		Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
        new AlertDialog.Builder(this)
			.setTitle("Choose a file to play")
			.setItems(filelist, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					File sel;
					if (filelist[which].equals("..")) sel=filepath.getParentFile();
					else sel=new File(filepath, filelist[which]);
					if (sel.isDirectory()) {
						filepath=sel;
						OpenClicked(null);
					} else {
						String file=sel.getPath();
						// free previous tempo, reverse & reverb handles
						BASS.BASS_StreamFree(chan);

						// reset button text
						((Button)findViewById(R.id.open)).setText("press here to open a file & play it...");

						if ((chan=BASS.BASS_StreamCreateFile(file, 0, 0, BASS.BASS_STREAM_DECODE|BASS.BASS_STREAM_PRESCAN))==0
							&& (chan=BASS.BASS_MusicLoad(file, 0, 0, BASS.BASS_MUSIC_DECODE|BASS.BASS_MUSIC_RAMP|BASS.BASS_STREAM_PRESCAN, 0))==0) {
							// whatever it is, it ain't playable
							Error("Selected file couldnt be loaded!");
							return;
						}

						// create new stream - decoded & reversed
						// 2 seconds decoding block as a decoding channel
						if ((chan=BASS_FX.BASS_FX_ReverseCreate(chan, 2, BASS.BASS_STREAM_DECODE|BASS_FX.BASS_FX_FREESOURCE))==0) {
							Error("Couldnt create a reversed stream!");
							BASS.BASS_StreamFree(chan);
							return;
						}

						// create a new stream - decoded & resampled
						if((chan=BASS_FX.BASS_FX_TempoCreate(chan, BASS.BASS_SAMPLE_LOOP|BASS_FX.BASS_FX_FREESOURCE))==0) {
							Error("Couldnt create a resampled stream!");
							BASS.BASS_StreamFree(chan);
							return;
						}

						// update the Button to show the loaded file name
						((Button)findViewById(R.id.open)).setText(file);

						// update tempo view
						((SeekBar)findViewById(R.id.sbTempo)).setProgress(((SeekBar)findViewById(R.id.sbTempo)).getMax()/2);
						((TextView)findViewById(R.id.txtTempo)).setText("Tempo = 0%");

						// set dx8 Reverb
						fx=BASS.BASS_ChannelSetFX(chan, BASS.BASS_FX_DX8_REVERB, 0);
						UpdateFX(((SeekBar)findViewById(R.id.reverb)).getProgress());

						// set Volume
						int p=((SeekBar)findViewById(R.id.sbVolume)).getProgress();
						BASS.BASS_ChannelSetAttribute(chan, BASS.BASS_ATTRIB_VOL,(float)p/100.0f);

						// set max to position SeekBar
						int len = (int)BASS.BASS_ChannelBytes2Seconds(chan, (long)BASS.BASS_ChannelGetLength(chan, BASS.BASS_POS_BYTE));
						((SeekBar)findViewById(R.id.sbPosition)).setMax(len);
						((SeekBar)findViewById(R.id.sbPosition)).setProgress(len); // set position to max

						// update the approximate time in seconds view
						UpdatePositionLabel();

						// restore direction text view
						((Button)findViewById(R.id.btnDirection)).setText("Playing Direction - Reverse");

						// play the new stream
						BASS.BASS_ChannelPlay(chan, false);
					}
				}
			})
	   		.show();
	}

	public void DirectionClicked(View v) {
		int srcChan = BASS_FX.BASS_FX_TempoGetSource(chan);
		BASS.FloatValue dir = new BASS.FloatValue();
		BASS.BASS_ChannelGetAttribute(srcChan, BASS_FX.BASS_ATTRIB_REVERSE_DIR, dir);

		if (dir.value<0) {
			BASS.BASS_ChannelSetAttribute(srcChan, BASS_FX.BASS_ATTRIB_REVERSE_DIR, BASS_FX.BASS_FX_RVS_FORWARD);
			((Button)findViewById(R.id.btnDirection)).setText("Playing Direction - Forward");
		} else {
			BASS.BASS_ChannelSetAttribute(srcChan, BASS_FX.BASS_ATTRIB_REVERSE_DIR, BASS_FX.BASS_FX_RVS_REVERSE);
			((Button)findViewById(R.id.btnDirection)).setText("Playing Direction - Reverse");
		}
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

		filepath=Environment.getExternalStorageDirectory();

		// initialize default output device
		if (!BASS.BASS_Init(-1, 44100, 0)) {
			Error("Can't initialize device");
			return;
		}

		// volume
        ((SeekBar)findViewById(R.id.sbVolume)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				BASS.BASS_ChannelSetAttribute(chan, BASS.BASS_ATTRIB_VOL, (float)progress/100.0f);
			}
		});

        // reverb
        SeekBar.OnSeekBarChangeListener osbcl=new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				UpdateFX(progress);
			}
		};
		((SeekBar)findViewById(R.id.reverb)).setOnSeekBarChangeListener(osbcl);

		// tempo
        ((SeekBar)findViewById(R.id.sbTempo)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				BASS.BASS_ChannelSetAttribute(chan, BASS_FX.BASS_ATTRIB_TEMPO, (float)(progress - seekBar.getMax()/2));
				
				String s=String.format("Tempo = %d%%", progress - seekBar.getMax()/2);
				((TextView)findViewById(R.id.txtTempo)).setText(s);
				
				// update the approximate time in seconds view
				UpdatePositionLabel();
			}
		});

        // position
        ((SeekBar)findViewById(R.id.sbPosition)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// change the position
				BASS.BASS_ChannelSetPosition(chan, BASS.BASS_ChannelSeconds2Bytes(chan, seekBar.getProgress()), BASS.BASS_POS_BYTE);
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				// update the approximate time in seconds view
				UpdatePositionLabel();
			}
		});
    }
    
    @Override
    public void onDestroy() {
    	BASS.BASS_Free();

    	super.onDestroy();
    }
}