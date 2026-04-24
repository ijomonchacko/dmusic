/*************************************************************************
 BPM.java - Copyright (c) 2012-2013 (: JOBnik! :) [Arthur Aminov, ISRAEL]
                                                  [http://www.jobnik.org]
                                                  [bass_fx @ jobnik .org]
 
 BASS_FX bpm with tempo & samplerate changers
*************************************************************************/

package com.example.bpm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import com.un4seen.bass.BASS;
import com.un4seen.bass.BASS_FX;

public class BPM extends Activity {
	int chan;							// tempo channel handle
	int bpmChan;						// decoding bpm handle
	float bpmValue;						// bpm value returned by BASS_FX_BPM_DecodeGet/GetBPM_Callback functions
	BASS.BASS_CHANNELINFO info = new BASS.BASS_CHANNELINFO();

	Float freq = 44100.0f;				// default frequency
	int min_freq = (int)(freq * 0.7f);	// default min frequency decrease by 30%
	int max_freq;						// calculated for seekBar as: max_freq - min_freq

	File filepath;
	String[] filelist;

	int req; // request number/counter
	Object lock = new Object();
	Timer timerBeatPos;
	TimerTask timerTaskBeatPos;

	class RunnableParam implements Runnable {
		Object param;
		RunnableParam(Object p) { param=p; }
		public void run() {}
	}
	
	// display error dialogs
	void Error(String es) {
		// get error code in current thread for display in UI thread
		String s=String.format("%s\n(error code: %d)", es, BASS.BASS_ErrorGetCode());
		runOnUiThread(new RunnableParam(s) {
            public void run() {
        		new AlertDialog.Builder(BPM.this)
    				.setMessage((String)param)
    				.setPositiveButton("OK", null)
    				.show();
            }
		});
	}

	// show the approximate position in MM:SS format according to Tempo change
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

	// calculate approximate bpm value according to Tempo change
	float GetNewBPM(float bpm)
	{
		return (bpm * BASS_FX.BASS_FX_TempoGetRateRatio(chan));
	}

	// get bpm value after period of time (called by BASS_FX_BPM_CallbackSet function)
	BASS_FX.BPMPROC GetBPM_Callback=new BASS_FX.BPMPROC() {
		public void BPMPROC(final int chan, final float bpm, Object user) {
			runOnUiThread(new Runnable() {
				public void run() {
					bpmValue = bpm;
					// update the bpm view
					if (bpm > 0) {
						String s=String.format("BPM: %.2f", GetNewBPM(bpm));
						((TextView)findViewById(R.id.txtBPM)).setText(s);
					}
				}
			});
		}
	};

	// get beat position in seconds (called by BASS_FX_BPM_BeatCallbackSet function)
	BASS_FX.BPMBEATPROC GetBeatPos_Callback=new BASS_FX.BPMBEATPROC() {
		public void BPMBEATPROC(int chan, double beatpos, Object user) {
			double curpos = BASS.BASS_ChannelBytes2Seconds(chan, BASS.BASS_ChannelGetPosition(chan, BASS.BASS_POS_BYTE));

			timerBeatPos = new Timer("alertTimer",true);
			timerTaskBeatPos = new timerTask_BeatPos();		  
			timerBeatPos.schedule(timerTaskBeatPos, (long)(beatpos - curpos) * 1000L);
		}
	};

	// timer task scheduler (called by timerBeatPos.schedule)
	private class timerTask_BeatPos extends TimerTask {
		@Override
		public void run() {
			if (BASS_FX.BASS_FX_TempoGetRateRatio(chan)!=0) {
				final double beatpos = BASS.BASS_ChannelBytes2Seconds(chan, BASS.BASS_ChannelGetPosition(chan, BASS.BASS_POS_BYTE)) / BASS_FX.BASS_FX_TempoGetRateRatio(chan);
				runOnUiThread(new Runnable() {
					public void run() {
						String s=String.format("Beat pos: %.2f", beatpos);
						((TextView)findViewById(R.id.txtBeatPos)).setText(s);
					}
				});
			}
			timerBeatPos.cancel();
		}
	}
	
	// get the bpm progress detection in percents of a decoding channel
	BASS_FX.BPMPROGRESSPROC GetBPM_ProgressCallback=new BASS_FX.BPMPROGRESSPROC() {
		public void BPMPROGRESSPROC(int chan, final float percent, Object user) {
			runOnUiThread(new Runnable() {
				public void run() {
					((ProgressBar)findViewById(R.id.pbProgressBPM)).setProgress((int)percent);	// update the progress bar
				}
			});
		}
	};

	public class DecodingBPM implements Runnable {
		boolean newStream;
		double startSec;
		double endSec;
		String fp;

		public DecodingBPM(boolean newStream, double startSec, double endSec, String fp) {
			this.newStream=newStream;
			this.startSec=startSec;
			this.endSec=endSec;
			this.fp=fp;
		}
		public void run() {
			int r,c=0;
			synchronized(lock) { // make sure only 1 thread at a time can do the following
				r=++req; // increment the request counter for this request
			}
			if (newStream) {
				// open the same file as played but for bpm decoding detection
				c = BASS.BASS_StreamCreateFile(fp, 0, 0, BASS.BASS_STREAM_DECODE);
				if (c==0) c = BASS.BASS_MusicLoad(fp, 0, 0, BASS.BASS_MUSIC_DECODE|BASS.BASS_MUSIC_PRESCAN, 0);
			}
			synchronized(lock) {
				if (r!=req && newStream) { // there is a newer request, discard this stream
					if (c!=0) BASS.BASS_StreamFree(c);
					return;
				}
				if (newStream) bpmChan=c; // this is now the current stream
			}

			// detect bpm in background and return progress in GetBPM_ProgressCallback function
    		if (bpmChan!=0) bpmValue = BASS_FX.BASS_FX_BPM_DecodeGet(bpmChan, startSec, endSec, 0, BASS_FX.BASS_FX_BPM_MULT2|BASS_FX.BASS_FX_FREESOURCE, GetBPM_ProgressCallback, 0);

    		// update the bpm view
    		if (bpmValue!=0) {
                runOnUiThread(new Runnable() {
                	public void run() {
            			String s=String.format("BPM: %.2f", GetNewBPM(bpmValue));
            			((TextView)findViewById(R.id.txtBPM)).setText(s);
                	}
                });
			}
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
						final String file=sel.getPath();

						// free decode bpm stream and resources
						BASS_FX.BASS_FX_BPM_Free(bpmChan);

						// free tempo, stream, music & bpm/beat callbacks
						BASS.BASS_StreamFree(chan);

						// reset button text
						((Button)findViewById(R.id.open)).setText("press here to open a file & play it...");

						// create decode stream/music channel
						if ((chan=BASS.BASS_StreamCreateFile(file, 0, 0, BASS.BASS_STREAM_DECODE))==0
							&& (chan=BASS.BASS_MusicLoad(file, 0, 0, BASS.BASS_MUSIC_DECODE|BASS.BASS_MUSIC_RAMP|BASS.BASS_MUSIC_PRESCAN, 1))==0) {
							// whatever it is, it ain't playable
							Error("Selected file couldnt be loaded!");
							return;
						}

						// get channel info
						BASS.BASS_ChannelGetInfo(chan, info);

						// set max length to position SeekBar
						int p = (int)BASS.BASS_ChannelBytes2Seconds(chan, (long)BASS.BASS_ChannelGetLength(chan, BASS.BASS_POS_BYTE));
						((SeekBar)findViewById(R.id.sbPosition)).setMax(p);
						((SeekBar)findViewById(R.id.sbPosition)).setProgress(0);

						// create a new stream - decoded & resampled :)
						if((chan=BASS_FX.BASS_FX_TempoCreate(chan, BASS.BASS_SAMPLE_LOOP|BASS_FX.BASS_FX_FREESOURCE))==0) {
							Error("Couldnt create a resampled stream!");
							BASS.BASS_StreamFree(chan);
							return;
						}

						// update the Button to show the loaded file name
						((Button)findViewById(R.id.open)).setText(file);

						// set samplerate seekBar max/min value by 30% according to current frequency
						min_freq = (int)(freq * 0.7f);
						max_freq = (int)(freq * 1.3f);
						((SeekBar)findViewById(R.id.sbSamplerate)).setMax(max_freq - min_freq);
						((SeekBar)findViewById(R.id.sbSamplerate)).setProgress(((SeekBar)findViewById(R.id.sbSamplerate)).getMax()/2);
						((TextView)findViewById(R.id.txtSamplerate)).setText("Samplerate = " + freq.intValue() + "Hz");

						// update tempo view
						((SeekBar)findViewById(R.id.sbTempo)).setProgress(((SeekBar)findViewById(R.id.sbTempo)).getMax()/2);
						((TextView)findViewById(R.id.txtTempo)).setText("Tempo = 0%");

						// update the approximate time in seconds view
						UpdatePositionLabel();

						// set the callback bpm and beat
						((TextView)findViewById(R.id.txtBeatPos)).setText("Beat pos: 0.00");
						((TextView)findViewById(R.id.txtBPM)).setText("BPM: 0.00");
						ChkBeatPosClicked(findViewById(R.id.chkBeatPos));
						ChkBPMClicked(findViewById(R.id.chkBPM));

						// play the new stream
						BASS.BASS_ChannelPlay(chan, false);

						// create bpmChan stream and get bpm value for etxtBPM seconds from current position
						double pos = (double)((SeekBar)findViewById(R.id.sbPosition)).getProgress();
						double period = Double.parseDouble(((EditText)findViewById(R.id.etxtBPM)).getText().toString());
						double maxpos = (double)((SeekBar)findViewById(R.id.sbPosition)).getMax();
						new Thread(new DecodingBPM(true, pos, (pos+period)>=maxpos?maxpos-1:pos+period,file)).start();
					}
				}
			})
	   		.show();
	}

	public void ChkBeatPosClicked(View v) {
		if (((CheckBox)v).isChecked())
			BASS_FX.BASS_FX_BPM_BeatCallbackSet(chan, GetBeatPos_Callback, 0);
		else
			BASS_FX.BASS_FX_BPM_BeatFree(chan);
	}

	public void ChkBPMClicked(View v) {
		if (((CheckBox)v).isChecked())
			BASS_FX.BASS_FX_BPM_CallbackSet(chan, GetBPM_Callback, Double.parseDouble(((EditText)findViewById(R.id.etxtBPM)).getText().toString()), 0, BASS_FX.BASS_FX_BPM_MULT2, 0);
		else
			BASS_FX.BASS_FX_BPM_Free(chan);
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

		// to prevent from EditText get focus on app start
		((EditText)findViewById(R.id.etxtBPM)).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent m) {
	        	((EditText)findViewById(R.id.etxtBPM)).setFocusable(true);
	        	((EditText)findViewById(R.id.etxtBPM)).setFocusableInTouchMode(true);
				return false;
			}
	    });

		// tempo
        ((SeekBar)findViewById(R.id.sbTempo)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if(BASS.BASS_ChannelIsActive(chan)==0) return;

				// set new tempo
				BASS.BASS_ChannelSetAttribute(chan, BASS_FX.BASS_ATTRIB_TEMPO, (float)(progress - seekBar.getMax()/2));

				// update the bpm view
				String s;
				s=String.format("BPM: %.2f", GetNewBPM(bpmValue));
				((TextView)findViewById(R.id.txtBPM)).setText(s);

				// update tempo text
				s=String.format("Tempo = %d%%", progress - seekBar.getMax()/2);
				((TextView)findViewById(R.id.txtTempo)).setText(s);
				
				// update the approximate time in seconds view
				UpdatePositionLabel();
			}
		});

		// samplerate
        ((SeekBar)findViewById(R.id.sbSamplerate)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if(BASS.BASS_ChannelIsActive(chan)==0) return;

				// set new samplerate
				BASS.BASS_ChannelSetAttribute(chan, BASS_FX.BASS_ATTRIB_TEMPO_FREQ, (float)(progress + min_freq));
				
				// update the bpm view
				String s;
				s=String.format("BPM: %.2f", GetNewBPM(bpmValue));
				((TextView)findViewById(R.id.txtBPM)).setText(s);

				// update samplerate text
				s=String.format("Samplerate = %dHz", (int)((progress + min_freq)));
				((TextView)findViewById(R.id.txtSamplerate)).setText(s);
				
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
				
				// get bpm value for etxtBPM seconds from current position
				double pos = (double)seekBar.getProgress();
				double period = Double.parseDouble(((EditText)findViewById(R.id.etxtBPM)).getText().toString());
				double maxpos = (double)seekBar.getMax();
				new Thread(new DecodingBPM(false, pos, (pos+period)>=maxpos?maxpos-1:pos+period,"")).start();
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