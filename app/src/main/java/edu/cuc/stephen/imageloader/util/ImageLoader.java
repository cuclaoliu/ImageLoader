package edu.cuc.stephen.imageloader.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 图片加载类
 * Created by stephen on 15-10-13.
 */
public class ImageLoader {

    private static ImageLoader instance;
    //图片缓存的核心对象
    private LruCache<String, Bitmap> lruCache;
    private ExecutorService threadPool;        //线程池
    private static final int DEFAULT_THREAD_COUNT = 1;

    public enum Type{
        FIFO, LIFO
    }
    private Type type = Type.LIFO; //队列调度方式
    private LinkedList<Runnable> taskQueue;

    //后台轮询线程
    private Thread poolThread;
    private Handler poolThreadHandler;
    private Semaphore semaphorePoolThreadHandler = new Semaphore(0);
    private Semaphore semaphoreThreadPool;
    //UI线程中的Handler
    private Handler uiHandler;
    public ImageLoader(int threadCount, Type type) {
        init(threadCount, type);
    }

    private void init(int threadCount, Type type) {
        //后台轮询线程
        poolThread = new Thread(){
            @Override
            public void run() {
                Looper.prepare();
                poolThreadHandler = new Handler(){
                    @Override
                    public void handleMessage(Message msg) {
                        //线程池去取出一个任务进行执行
                        Runnable task = getTask();
                        if(task!=null)
                            threadPool.execute(task);
                        try {
                            semaphoreThreadPool.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                //释放一个信号量
                semaphorePoolThreadHandler.release();
                Looper.loop();
            }
        };
        poolThread.start();
        //获取应用的最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory/8;

        lruCache = new LruCache<String, Bitmap>(cacheMemory){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes()*value.getHeight();
            }
        };

        //创建线程池
        threadPool = Executors.newFixedThreadPool(threadCount);
        taskQueue = new LinkedList<>();
        this.type = type;

        semaphoreThreadPool = new Semaphore(threadCount);
    }

    private Runnable getTask() {
        if(type == Type.FIFO){
            return taskQueue.removeFirst();
        }else if(type == Type.LIFO){
            return taskQueue.removeLast();
        }
        return null;
    }

    //根据path
    public void loadImage(final String path, final ImageView imageView) {
        imageView.setTag(path);
        if(uiHandler==null){
            uiHandler = new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    //获取得到图片，为imageView回调设置图片
                    ImageBeanHolder holder = (ImageBeanHolder) msg.obj;
                    Bitmap bitmap = holder.bitmap;
                    ImageView iv = holder.imageView;
                    String path = holder.path;
                    //将path与getTag存储路径进行比较
                    if(iv.getTag().toString().equals(path)){
                        iv.setImageBitmap(bitmap);
                    }
                }
            };
        }
        //根据path在缓存中获取bitmap
        final Bitmap bitmap = getBitmapFromLruCache(path);
        if(bitmap!=null){
            refreshBitmap(bitmap, path, imageView);
        }else {
            addTasks(new Runnable() {
                @Override
                public void run() {
                    //加载图片
                    //图片的压缩
                    //1. 获得图片需要显示的大小
                    ImageSize imageSize = getImageViewSize(imageView);
                    //2. 压缩图片
                    Bitmap bitmap = decodeSampledBitmapFromPath(path, imageSize);
                    //3. 将图片加入到缓存
                    addBitmapToLruCache(path, bitmap);

                    refreshBitmap(bitmap, path, imageView);

                    semaphoreThreadPool.release();
                }
            });
        }
    }

    private void refreshBitmap(Bitmap bitmap, String path, ImageView imageView) {
        Message message = Message.obtain();
        ImageBeanHolder holder = new ImageBeanHolder();
        holder.bitmap = bitmap;
        holder.path = path;
        holder.imageView = imageView;
        message.obj = holder;
        uiHandler.sendMessage(message);
    }

    private void addBitmapToLruCache(String path, Bitmap bm) {
        if(getBitmapFromLruCache(path)!=null){
            if(bm != null){
                lruCache.put(path, bm);
            }
        }
    }

    private Bitmap decodeSampledBitmapFromPath(String path, ImageSize imageSize) {
        //不真正加载图片来获取图片大小
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, imageSize.width, imageSize.height);
        //使用获取到的inSampleSize再次解析图片
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int requireWidth, int requireHeight) {
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;
        if(width>requireWidth || height>requireHeight){
            int widthRatio = Math.round((float)width/requireWidth);
            int heightRatio = Math.round((float)height/requireHeight);
            inSampleSize = Math.max(widthRatio, heightRatio);
        }
        return inSampleSize;
    }

    //根据imageView 获取适当的压缩宽和高
    private ImageSize getImageViewSize(ImageView imageView) {
        ImageSize imageSize = new ImageSize();
        ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
        int width = imageView.getWidth();//获取imageView的实际宽度
        if(width <= 0){
            width = layoutParams.width; //imageView在layout中的参数
        }
        if(width <= 0){
            width = imageView.getMaxWidth();        //检查最大值；API16以上才能使用的方法
            //width = getImageViewFieldValue(imageView, "mMaxWidth");       //API8使用方法
        }
        if(width <= 0){
            width = displayMetrics.widthPixels;
        }
        imageSize.width = width;
        int height = imageView.getHeight();//获取imageView的实际高度
        if(height <= 0){
            height = layoutParams.height; //imageView在layout中的参数
        }
        if(height <= 0){
            height = imageView.getMaxHeight();        //检查最大值
            //height = getImageViewFieldValue(imageView, "mMaxHeight");
        }
        if(height <= 0){
            height = displayMetrics.heightPixels;
        }
        imageSize.height = height;
        return imageSize;
    }

    //通过反射获取imageView的某个属性值
    private static int getImageViewFieldValue(Object object, String fieldName){
        int value = 0;
        Field field = null;
        try {
            field = ImageView.class.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        if (field != null) {
            field.setAccessible(true);
            try {
                int fieldValue = field.getInt(object);
                if(fieldValue>0 && fieldValue < Integer.MAX_VALUE){
                    value = fieldValue;
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return value;
    }

    private class ImageSize{
        int width;
        int height;
    }
    private synchronized void addTasks(Runnable runnable) {
        taskQueue.add(runnable);
        //poolThreadHandler可能为null
        try {
            if (poolThreadHandler == null)
                semaphorePoolThreadHandler.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        poolThreadHandler.sendEmptyMessage(0x110);
    }

    private class ImageBeanHolder{
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }

    private Bitmap getBitmapFromLruCache(String key) {
        return lruCache.get(key);
    }

    public static ImageLoader getInstance() {
        if(instance == null){      //为了提高效率，可以过滤后续代码
            synchronized (ImageLoader.class){   //避免两个线程同时到达这里
                if(instance==null)
                    instance = new ImageLoader(DEFAULT_THREAD_COUNT, Type.LIFO);
            }
        }
        return instance;
    }

    public static ImageLoader getInstance(int threadCount, Type type) {
        if(instance == null){      //为了提高效率，可以过滤后续代码
            synchronized (ImageLoader.class){   //避免两个线程同时到达这里
                if(instance==null)
                    instance = new ImageLoader(threadCount, type);
            }
        }
        return instance;
    }
}
