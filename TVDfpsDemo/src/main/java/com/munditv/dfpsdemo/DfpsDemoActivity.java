package com.munditv.dfpsdemo;

import java.nio.ByteBuffer;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class DfpsDemoActivity extends Activity implements SurfaceHolder.Callback {
	private static final String SAMPLE[] = { 
		"20150320-01.MP4",
		"20150320-02.MP4", 
		"20150320-03.MP4", 
		"20150320-04.MP4", 
		"20150320-05.MP4",
		"20150320-06.MP4",
		"20150320-07.MP4",
		"20150320-08.MP4",
		"20150320-09.MP4",
		"20150320-10.MP4",
		"20150320-11.MP4",
		"20150320-12.MP4",
		"20150320-13.MP4",
		"20150320-14.MP4",
		"20150320-15.MP4",
		};
	
	private String movie;
	private String weburl = "http://209.95.35.8/vod/Test/Other/日月潭路段/";
	private PlayerThread mPlayer = null;
    private int run=10;
    private int delay=0;
    private int index=0;
    private boolean start=true;
    private boolean pause=false;
    private boolean quit = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		movie = weburl+ SAMPLE[index];
		SurfaceView sv = new SurfaceView(this);
		sv.getHolder().addCallback(this);
		setContentView(sv);
	}

	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (mPlayer == null) {
			mPlayer = new PlayerThread(holder.getSurface());
			mPlayer.start();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (mPlayer != null) {
			mPlayer.interrupt();
		}
	}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	switch(keyCode) {
    	case KeyEvent.KEYCODE_DPAD_DOWN:
    		run--;
    		if(run<0) {
    			run=0;
    		}
    		delay = 100-run*10+1;
    		break;
    	case KeyEvent.KEYCODE_DPAD_UP:
    		run++;
    		if(run > 10) run=10;
    		delay = 100-run*10+1;
    		pause=false;
    		break;
    	case KeyEvent.KEYCODE_DPAD_RIGHT:
    		quit=true;
    		break;
    	case KeyEvent.KEYCODE_DPAD_LEFT:
    		run=0;
    		index++;
    		if(index > 4) index=0;
    		movie = weburl + SAMPLE[index];
    		mPlayer.setMovie();
    		break;
    	}
		return super.onKeyDown(keyCode, event);
		
	}
	
	private class PlayerThread extends Thread {
		private MediaExtractor extractor;
		private MediaCodec decoder;
		private Surface surface;

		public PlayerThread(Surface surface) {
			this.surface = surface;
			start = true;
		}

		public void setMovie() {
			if(decoder != null) {
				decoder.stop();
				decoder.release();
				decoder = null;
			}
			if(extractor != null) {
				extractor.release();
				extractor = null;
			}
			
			extractor = new MediaExtractor();
			try {
				extractor.setDataSource(movie);
			} catch(Exception e) {
				e.printStackTrace();
			}

			for (int i = 0; i < extractor.getTrackCount(); i++) {
				MediaFormat format = extractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				if (mime.startsWith("video/")) {
					Log.d("Decoder", "mime type:" + mime);
					extractor.selectTrack(i);
					try {
						decoder = MediaCodec.createDecoderByType(mime);
						decoder.configure(format, surface, null, 0);
					} catch(Exception e) {
						e.printStackTrace();
					}
					break;
				}
			}

			if (decoder == null) {
				Log.e("DecodeActivity", "Can't find video info!");
				return;
			}
			decoder.start();
		}
		
		
		@Override
		public void run() {
			setMovie();
			
			ByteBuffer[] inputBuffers = decoder.getInputBuffers();
			ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			boolean isEOS = false;
			long startMs = System.currentTimeMillis();
			
			extractor.seekTo(0*1000000,0); // shift to 0secs for start
			
			while (!Thread.interrupted()) {
				if (!isEOS) {
					int inIndex = decoder.dequeueInputBuffer(10000);
					if (inIndex >= 0) {
						ByteBuffer buffer = inputBuffers[inIndex];
						int sampleSize = extractor.readSampleData(buffer, 0);
						if (sampleSize < 0) {
							// We shouldn't stop the playback at this point, just pass the EOS
							// flag to decoder, we will get it again from the
							// dequeueOutputBuffer
							Log.d("DecodeActivity", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
							decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							isEOS = true;
						} else {
							decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
							extractor.advance();
						}
					}
				}

				int outIndex = decoder.dequeueOutputBuffer(info, 10000);
				if(run==0) pause = true; 
				switch (outIndex) {
				case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
					Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
					outputBuffers = decoder.getOutputBuffers();
					break;
				case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
					Log.d("DecodeActivity", "New format " + decoder.getOutputFormat());
					break;
				case MediaCodec.INFO_TRY_AGAIN_LATER:
					Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
					break;
				default:
					ByteBuffer buffer = outputBuffers[outIndex];
					Log.v("DecodeActivity", "We can't use this buffer but render it due to the API limit, " + buffer);

					try {
						sleep(delay) ;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					decoder.releaseOutputBuffer(outIndex, true);
					break;
				}

				// All decoded frames have been rendered, we can stop playing now
				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.d("DecodeActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
					break;
				}

				if(quit) break;

				while(pause) {
					try {
						sleep(10);
						if(run > 0) pause=false;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
				if(!start) break;
			}

			decoder.stop();
			decoder.release();
			extractor.release();
		}
	}
}