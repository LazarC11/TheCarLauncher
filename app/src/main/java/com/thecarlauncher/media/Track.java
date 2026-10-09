package com.thecarlauncher.media;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;

public final class Track {
    public final long id, albumId, artistId, duration;
    public final String title, artist, album, folder;

    public Track(long id, String title, String artist, String album, long albumId, long artistId,
                 long duration, String folder) {
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
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }
}
