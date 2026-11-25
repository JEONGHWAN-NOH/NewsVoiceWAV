package com.newsvoice.radio;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.*;import android.provider.OpenableColumns;import java.io.*;
public final class ShareProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File resolve(Uri uri){String name=uri.getLastPathSegment();if(!"broadcast.mp4".equals(name)&&!"broadcast.mp3".equals(name)&&!"broadcast.m4a".equals(name))throw new IllegalArgumentException();return new File(getContext().getFilesDir(),name);}
    public String getType(Uri uri){String n=resolve(uri).getName();return n.endsWith(".mp4")?"video/mp4":n.endsWith(".m4a")?"audio/mp4":"audio/mpeg";}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    public Cursor query(Uri uri,String[] projection,String s,String[] a,String order){File f=resolve(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]=f.getName();else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=f.length();}cursor.addRow(values);return cursor;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
