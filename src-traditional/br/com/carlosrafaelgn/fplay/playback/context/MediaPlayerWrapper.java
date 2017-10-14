//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013-2014, Carlos Rafael Gimenes das Neves
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
// ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// The views and conclusions contained in the software and documentation are those
// of the authors and should not be interpreted as representing official policies,
// either expressed or implied, of the FreeBSD Project.
//
// https://github.com/carlosrafaelgn/FPlayAndroid
//
package br.com.carlosrafaelgn.fplay.playback.context;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.util.BufferedMediaDataSource;

final class MediaPlayerWrapper extends MediaPlayerBase implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnSeekCompleteListener {
	private final MediaPlayer player;
	private volatile boolean prepared;
	private OnCompletionListener completionListener;
	private OnErrorListener errorListener;
	private OnInfoListener infoListener;
	private OnPreparedListener preparedListener;
	private OnSeekCompleteListener seekCompleteListener;

	public MediaPlayerWrapper() {
		player = new MediaPlayer();
	}

	@Override
	public int getSrcSampleRate() {
		return 0;
	}

	@Override
	public int getChannelCount() {
		return 0;
	}

	@Override
	public void start() {
		player.start();
	}

	@Override
	public void pause() {
		player.pause();
	}

	@Override
	public void setDataSource(String path) throws IOException {
		prepared = false;
		if (path.startsWith("file:")) {
			final Uri uri = Uri.parse(path);
			path = uri.getPath();
		}
		if (path.charAt(0) == File.separatorChar) {
			final File file = new File(path);
			ParcelFileDescriptor fileDescriptor = null;
			final int filePrefetchSize = Player.filePrefetchSize;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && filePrefetchSize > 0) {
				player.setDataSource(new BufferedMediaDataSource(path, file.length(), filePrefetchSize));
			} else {
				try {
					fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
					player.setDataSource(fileDescriptor.getFileDescriptor(), 0, fileDescriptor.getStatSize());
				} finally {
					try {
						if (fileDescriptor != null)
							fileDescriptor.close();
					} catch (Throwable ex) {
						//just ignore
					}
				}
			}
		} else {
			player.setDataSource(path);
		}
	}

	private void setAudioAttributes() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			player.setAudioAttributes(new AudioAttributes.Builder()
				.setLegacyStreamType(AudioManager.STREAM_MUSIC)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.build());
		} else {
			//noinspection deprecation
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		}
	}

	@Override
	public void prepare() throws IOException {
		setAudioAttributes();
		prepared = false;
		player.prepare();
		prepared = true;
	}

	@Override
	public void prepareAsync() {
		setAudioAttributes();
		prepared = false;
		player.prepareAsync();
	}

	@Override
	public void seekToAsync(int msec) {
		player.seekTo(msec);
	}

	@Override
	public void release() {
		prepared = false;
		player.release();
		completionListener = null;
		errorListener = null;
		infoListener = null;
		preparedListener = null;
		seekCompleteListener = null;
	}

	@Override
	public void reset() {
		prepared = false;
		try {
			player.reset();
		} catch (Throwable ex) {
			//we will just ignore, since it is a rare case
			//when Player class called reset() on a player that
			//had already been released!
		}
	}

	@Override
	public int getAudioSessionId() {
		return player.getAudioSessionId();
	}

	@Override
	public void setAudioSessionId(int sessionId) {
		player.setAudioSessionId(sessionId);
	}

	@Override
	public int getDuration() {
		//calling getDuration() while the player is not
		//fully prepared causes exception on a few devices,
		//and causes error (-38,0) on others
		if (!prepared)
			return -1;
		try {
			return player.getDuration();
		} catch (Throwable ex) {
			return -1;
		}
	}

	@Override
	public int getCurrentPosition() {
		//calling getCurrentPosition() while the player is not
		//fully prepared causes exception on a few devices,
		//and causes error (-38,0) on others
		if (!prepared)
			return -1;
		try {
			final int position = player.getCurrentPosition();
			//sometimes, when the player is still loading, player.getCurrentPosition() returns weird values!
			return ((position <= 10800000 || position <= player.getDuration()) ? position : -1);
		} catch (Throwable ex) {
			return -1;
		}
	}

	@Override
	public int getHttpPosition() {
		return -1;
	}

	@Override
	public int getHttpFilledBufferSize() {
		return 0;
	}

	@Override
	public void setVolume(float leftVolume, float rightVolume) {
		player.setVolume(leftVolume, rightVolume);
	}

	@Override
	public void setWakeMode(Context context, int mode) {
		player.setWakeMode(context, mode);
	}

	@Override
	public void setOnCompletionListener(OnCompletionListener listener) {
		completionListener = listener;
		player.setOnCompletionListener((listener == null) ? null : this);
	}

	@Override
	public void setOnErrorListener(OnErrorListener listener) {
		errorListener = listener;
		player.setOnErrorListener((listener == null) ? null : this);
	}

	@Override
	public void setOnInfoListener(OnInfoListener listener) {
		infoListener = listener;
		player.setOnInfoListener((listener == null) ? null : this);
	}

	@Override
	public void setOnPreparedListener(OnPreparedListener listener) {
		preparedListener = listener;
		player.setOnPreparedListener((listener == null) ? null : this);
	}

	@Override
	public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
		seekCompleteListener = listener;
		player.setOnSeekCompleteListener((listener == null) ? null : this);
	}

	@Override
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public void setNextMediaPlayer(MediaPlayerBase next) {
		player.setNextMediaPlayer((next == null) ? null : ((MediaPlayerWrapper)next).player);
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		prepared = false;
		if (completionListener != null)
			completionListener.onCompletion(this);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		prepared = false;
		return ((errorListener != null) && errorListener.onError(this, what, extra));
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		return ((infoListener != null) && infoListener.onInfo(this, what, extra, null));
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		prepared = true;
		if (preparedListener != null)
			preparedListener.onPrepared(this);
	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		if (seekCompleteListener != null)
			seekCompleteListener.onSeekComplete(this);
	}
}
