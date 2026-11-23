package com.newsvoice.tts;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Private normalized cover. Atomic replacement keeps an earlier choice on import failure. */
final class BroadcastCover {
    private static File file(Context context) {
        return new File(context.getFilesDir(), "broadcast-cover.png");
    }

    static void restore(Context context) throws IOException {
        Files.deleteIfExists(file(context).toPath());
    }

    static Bitmap read(Context context) {
        File selected = file(context);
        return selected.isFile() ? BitmapFactory.decodeFile(selected.getPath()) : null;
    }

    static void select(Context context, Uri uri) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
        int width = NewsVideoComposer.VIDEO_WIDTH;
        int height = NewsVideoComposer.VIDEO_HEIGHT;
        Bitmap image = ImageDecoder.decodeBitmap(source, (decoder, info, unused) -> {
            String type = info.getMimeType();
            if (!"image/jpeg".equals(type) && !"image/webp".equals(type) && !"image/png".equals(type))
                throw new IllegalArgumentException("WebP, JPG, PNG 표지를 선택하세요.");
            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
            float fit = Math.min(1f, Math.min((float)width/w, (float)height/h));
            decoder.setTargetSize(Math.max(1, Math.round(w*fit)), Math.max(1, Math.round(h*fit)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        Bitmap frame = null;
        File temp = null;
        try {
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            canvas.drawColor(Color.BLACK);
            float scale = Math.min((float)width/image.getWidth(), (float)height/image.getHeight());
            float x = (width - image.getWidth()*scale)/2, y = (height - image.getHeight()*scale)/2;
            canvas.drawBitmap(image, null, new RectF(x, y, width-x, height-y), new Paint(Paint.FILTER_BITMAP_FLAG));
            temp = File.createTempFile("broadcast-cover", ".tmp", context.getFilesDir());
            try (FileOutputStream out = new FileOutputStream(temp)) {
                if (!frame.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("표지 저장 실패");
            }
            Files.move(temp.toPath(), file(context).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            image.recycle();
            if (frame != null) frame.recycle();
            if (temp != null) temp.delete();
        }
    }
}
