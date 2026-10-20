package com.thecarlauncher.media;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;

public final class Track {
    public final long id, albumId, artistId, duration;
    public final String title, artist, album, folder;
    /** File path for songs read straight from a USB stick / SD card (not in MediaStore). */
    public final String path;
    /** Music video (MP4 …): played with its picture where the cover is. */
    public final boolean video;

    public Track(long id, String title, String artist, String album, long albumId, long artistId,
                 long duration, String folder) {
        this(id, title, artist, album, albumId, artistId, duration, folder, null);
    }

    public Track(long id, String title, String artist, String album, long albumId, long artistId,
                 long duration, String folder, String path) {
        this.path = path;
        this.video = path != null && MusicLibrary.isVideo(path);
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.artistId = artistId;
        this.duration = duration;
        this.folder = folder;
    }

    public Uri uri() {
        if (path != null) return Uri.fromFile(new java.io.File(path));
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }
}
