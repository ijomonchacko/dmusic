/*
	BASS DSP example
	Copyright (c) 2000-2021 Un4seen Developments Ltd.
*/

package com.example.dsptest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

import com.un4seen.bass.BASS;

public class DSPTest extends Activity {
	int chan;                // channel handle
	File filepath;
	String[] filelist;

	class RunnableParam implements Runnable {
		Object param;

		RunnableParam(Object p) {
			param = p;
		}

		public void run() {
		}
	}

	// display error messages
	void Error(String es) {
		// get error code in current thread for display in UI thread
		String s = String.format("%s\n(error code: %d)", es, BASS.BASS_ErrorGetCode());
		runOnUiThread(new RunnableParam(s) {
			public void run() {
				new AlertDialog.Builder(DSPTest.this)
						.setMessage((String) param)
						.setPositiveButton("OK", null)
						.show();
			}
		});
	}

	// "rotate"
	int rotdsp;        // DSP handle
	float[] rotpos = new float[2];    // sin/cos pos

	BASS.DSPPROC Rotate = new BASS.DSPPROC() {
		public void DSPPROC(int handle, int channel, ByteBuffer buffer, int length, Object user) {
			buffer.order(null); // little-endian
			FloatBuffer ibuffer = buffer.asFloatBuffer();
			float[] d = new float[length / 4]; // allocate array for data
			ibuffer.get(d); // copy data from buffer to array
			for (int a = 0; a < length / 4; a += 2) {
				d[a] = d[a] * rotpos[0];
				d[a + 1] = d[a + 1] * rotpos[1];
				rotpos[0] -= rotpos[1] * 0.000015f;
				rotpos[1] += rotpos[0] * 0.000015f;
			}
			ibuffer.rewind();
			ibuffer.put(d); // copy modified data back to buffer
		}
	};

	// "echo"
	int echdsp = 0;    // DSP handle
	static final int ECHBUFLEN = 1200;    // buffer length
	float[][] echbuf;    // buffer
	int echpos;    // cur.pos

	BASS.DSPPROC Echo = new BASS.DSPPROC() {
		public void DSPPROC(int handle, int channel, ByteBuffer buffer, int length, Object user) {
			buffer.order(null); // little-endian
			FloatBuffer ibuffer = buffer.asFloatBuffer();
			float[] d = new float[length / 4]; // allocate array for data
			ibuffer.get(d); // copy data from buffer to array
			for (int a = 0; a < length / 4; a += 2) {
				float l = d[a] + (echbuf[echpos][1] / 2);
				float r = d[a + 1] + (echbuf[echpos][0] / 2);
				if (true) { // false=echo, true=basic "bathroom" reverb
					echbuf[echpos][0] = d[a] = l;
					echbuf[echpos][1] = d[a + 1] = r;
				} else {
					echbuf[echpos][0] = d[a];
					echbuf[echpos][1] = d[a + 1];
				}
				d[a] = l;
				d[a + 1] = r;
				echpos++;
				if (echpos == ECHBUFLEN) echpos = 0;
			}
			ibuffer.rewind();
			ibuffer.put(d); // copy modified data back to buffer
		}
	};

	// "flanger"
	int fladsp = 0;    // DSP handle
	static final int FLABUFLEN = 350;    // buffer length
	float[][] flabuf;    // buffer
	int flapos;    // cur.pos
	float flas, flasinc;    // sweep pos/increment

	BASS.DSPPROC Flange = new BASS.DSPPROC() {
		public void DSPPROC(int handle, int channel, ByteBuffer buffer, int length, Object user) {
			buffer.order(null); // little-endian
			FloatBuffer ibuffer = buffer.asFloatBuffer();
			float[] d = new float[length / 4]; // allocate array for data
			ibuffer.get(d); // copy data from buffer to array
			for (int a = 0; a < length / 4; a += 2) {
				int p1 = (flapos + (int) flas) % FLABUFLEN;
				int p2 = (p1 + 1) % FLABUFLEN;
				float f = flas - (int) flas;
				float s;
				s = (d[a] + flabuf[p1][0] + (flabuf[p2][0] - flabuf[p1][0]) * f) * 0.7f;
				flabuf[flapos][0] = d[a];
				d[a] = s;
				s = (d[a + 1] + flabuf[p1][1] + (flabuf[p2][1] - flabuf[p1][1]) * f) * 0.7f;
				flabuf[flapos][1] = d[a + 1];
				d[a + 1] = s;
				flapos++;
				if (flapos == FLABUFLEN) flapos = 0;
				flas += flasinc;
				if (flas < 0 || flas > FLABUFLEN - 1) {
					flasinc = -flasinc;
					flas += flasinc;
				}
			}
			ibuffer.rewind();
			ibuffer.put(d); // copy modified data back to buffer
		}
	};

	public void OpenClicked(View v) {
		String[] list = filepath.list();
		if (list == null) list = new String[0];
		if (!filepath.getPath().equals("/")) {
			filelist = new String[list.length + 1];
			filelist[0] = "..";
			System.arraycopy(list, 0, filelist, 1, list.length);
		} else
			filelist = list;
		Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
		new AlertDialog.Builder(this)
				.setTitle("Choose a file to play")
				.setItems(filelist, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						File sel;
						if (filelist[which].equals("..")) sel = filepath.getParentFile();
						else sel = new File(filepath, filelist[which]);
						if (sel.isDirectory()) {
							filepath = sel;
							OpenClicked(null);
						} else {
							String file = sel.getPath();
							BASS.BASS_ChannelFree(chan); // free the old channel
							if ((chan = BASS.BASS_StreamCreateFile(file, 0, 0, BASS.BASS_SAMPLE_LOOP | BASS.BASS_SAMPLE_FLOAT)) == 0
									&& (chan = BASS.BASS_MusicLoad(file, 0, 0, BASS.BASS_MUSIC_RAMPS | BASS.BASS_SAMPLE_LOOP | BASS.BASS_SAMPLE_FLOAT, 1)) == 0) {
								((Button) findViewById(R.id.open)).setText("Open file...");
								Error("Can't play the file");
								return;
							}
							BASS.BASS_CHANNELINFO info = new BASS.BASS_CHANNELINFO();
							BASS.BASS_ChannelGetInfo(chan, info);
							if (info.chans != 2) { // the DSP expects stereo
								((Button) findViewById(R.id.open)).setText("Open file...");
								BASS.BASS_ChannelFree(chan);
								Error("only stereo sources are supported");
								return;
							}
							((Button) findViewById(R.id.open)).setText(sel.getName());
							// setup DSPs on new channel
							RotateClicked(findViewById(R.id.rotate));
							EchoClicked(findViewById(R.id.echo));
							FlangerClicked(findViewById(R.id.flanger));
							BASS.BASS_ChannelPlay(chan, false);
						}
					}
				})
				.show();
	}

	public void RotateClicked(View v) {
		if (((CheckBox) v).isChecked()) {
			rotpos[0] = rotpos[1] = (float) Math.sin(Math.PI / 4);
			rotdsp = BASS.BASS_ChannelSetDSP(chan, Rotate, 0, 2);
		} else
			BASS.BASS_ChannelRemoveDSP(chan, rotdsp);
	}

	public void EchoClicked(View v) {
		if (((CheckBox) v).isChecked()) {
			echbuf = new float[ECHBUFLEN][2];
			echpos = 0;
			echdsp = BASS.BASS_ChannelSetDSP(chan, Echo, 0, 1);
		} else
			BASS.BASS_ChannelRemoveDSP(chan, echdsp);
	}

	public void FlangerClicked(View v) {
		if (((CheckBox) v).isChecked()) {
			flabuf = new float[FLABUFLEN][2];
			flas = FLABUFLEN / 2;
			flasinc = 0.002f;
			flapos = 0;
			fladsp = BASS.BASS_ChannelSetDSP(chan, Flange, 0, 0);
		} else
			BASS.BASS_ChannelRemoveDSP(chan, fladsp);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		if (Build.VERSION.SDK_INT >= 23)
			requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);

		filepath = Environment.getExternalStorageDirectory();

		// initialize default output device
		if (!BASS.BASS_Init(-1, 44100, 0)) {
			Error("Can't initialize device");
			return;
		}

		// enable floating-point DSP (not really necessary as the channels will be floating-point anyway)
		BASS.BASS_SetConfig(BASS.BASS_CONFIG_FLOATDSP, 1);
	}

	@Override
	public void onDestroy() {
		BASS.BASS_Free();

		super.onDestroy();
	}
}