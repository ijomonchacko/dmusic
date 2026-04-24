/*
	BASS plugins example
	Copyright (c) 2005-2022 Un4seen Developments Ltd.
*/

package com.example.plugins;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.Arrays;

import com.un4seen.bass.BASS;

public class Plugins extends Activity {
	int chan;                // channel handle

	Handler handler = new Handler();
	Runnable timer;
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
				new AlertDialog.Builder(Plugins.this)
						.setMessage((String) param)
						.setPositiveButton("OK", null)
						.show();
			}
		});
	}

	// translate a CTYPE value to text
	String GetCTypeString(int ctype, int plugin) {
		if (plugin != 0) { // using a plugin
			BASS.BASS_PLUGININFO pinfo = BASS.BASS_PluginGetInfo(plugin); // get plugin info
			int a;
			for (a = 0; a < pinfo.formatc; a++) {
				if (pinfo.formats[a].ctype == ctype) // found a "ctype" match...
					return pinfo.formats[a].name; // return its name
			}
		}
		// check built-in stream formats...
		if (ctype == BASS.BASS_CTYPE_STREAM_OGG) return "Ogg Vorbis";
		if (ctype == BASS.BASS_CTYPE_STREAM_MP1) return "MPEG layer 1";
		if (ctype == BASS.BASS_CTYPE_STREAM_MP2) return "MPEG layer 2";
		if (ctype == BASS.BASS_CTYPE_STREAM_MP3) return "MPEG layer 3";
		if (ctype == BASS.BASS_CTYPE_STREAM_AIFF) return "Audio IFF";
		if (ctype == BASS.BASS_CTYPE_STREAM_WAV_PCM) return "PCM WAVE";
		if (ctype == BASS.BASS_CTYPE_STREAM_WAV_FLOAT) return "Floating-point WAVE";
		if ((ctype & BASS.BASS_CTYPE_STREAM_WAV) != 0) // other WAVE codec
			return "WAVE";
		return "?";
	}

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
							BASS.BASS_StreamFree(chan); // free the old stream
							if ((chan = BASS.BASS_StreamCreateFile(file, 0, 0, BASS.BASS_SAMPLE_LOOP | BASS.BASS_SAMPLE_FLOAT)) == 0) {
								((Button) findViewById(R.id.open)).setText("Open file...");
								((TextView) findViewById(R.id.info)).setText("");
								((TextView) findViewById(R.id.positiontext)).setText("");
								((SeekBar) findViewById(R.id.position)).setMax(0);
								Error("Can't play the file");
								return;
							}
							((Button) findViewById(R.id.open)).setText(sel.getName());
							// display the file type
							BASS.BASS_CHANNELINFO info = new BASS.BASS_CHANNELINFO();
							BASS.BASS_ChannelGetInfo(chan, info);
							String ctype;
							if (info.ctype == BASS.BASS_CTYPE_STREAM_AM)
								ctype = (String) BASS.BASS_ChannelGetTags(chan, BASS.BASS_TAG_AM_MIME);
							else
								ctype = GetCTypeString(info.ctype, info.plugin);
							((TextView) findViewById(R.id.info)).setText(String.format("channel type = %x (%s)", info.ctype, ctype));
							// update scroller range
							long len = BASS.BASS_ChannelGetLength(chan, BASS.BASS_POS_BYTE);
							if (len == -1) len = 0; // unknown length
							((SeekBar) findViewById(R.id.position)).setMax((int)(BASS.BASS_ChannelBytes2Seconds(chan, len) * 1000));
							BASS.BASS_ChannelPlay(chan, false);
							handler.removeCallbacks(timer);
							handler.postDelayed(timer, 100);
						}
					}
				})
				.show();
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

		// look for plugins
		String plugins = "";
		String[] list = new File(getApplicationInfo().nativeLibraryDir).list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.matches("libbass.+\\.so");
			}
		});
		for (String s : list) {
			int plug = BASS.BASS_PluginLoad(s, 0);
			if (plug != 0) { // plugin loaded
				plugins += s + "\n"; // add it to the list
			}
		}
		if (plugins.isEmpty()) plugins = "no plugins - visit the BASS webpage to get some\n";
		((TextView) findViewById(R.id.plugins)).setText(plugins);

		((SeekBar) findViewById(R.id.position)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser)
					BASS.BASS_ChannelSetPosition(chan, BASS.BASS_ChannelSeconds2Bytes(chan, progress / 1000.0), BASS.BASS_POS_BYTE);
			}
		});

		// timer to update the position display
		timer = new Runnable() {
			public void run() {
				if (chan != 0) {
					double len = BASS.BASS_ChannelBytes2Seconds(chan, BASS.BASS_ChannelGetLength(chan, BASS.BASS_POS_BYTE));
					double pos = BASS.BASS_ChannelBytes2Seconds(chan, BASS.BASS_ChannelGetPosition(chan, BASS.BASS_POS_BYTE));
					((TextView) findViewById(R.id.positiontext)).setText(String.format("%d:%02d / %d:%02d",
							(int)pos / 60, (int)pos % 60, (int)len / 60, (int)len % 60));
					((SeekBar) findViewById(R.id.position)).setProgress((int)(pos * 1000));
					handler.postDelayed(this, 100);
				}
			}
		};
	}

	@Override
	public void onDestroy() {
		BASS.BASS_Free();
		BASS.BASS_PluginFree(0); // unload all plugins

		super.onDestroy();
	}
}