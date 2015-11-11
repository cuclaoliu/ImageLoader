package edu.cuc.stephen.imageloader;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.cuc.stephen.imageloader.util.ImageLoader;

public class MainActivity extends AppCompatActivity {

    private GridView gridView;
    private List<String> images;

    private RelativeLayout bottomLayout;
    private TextView dirName;
    private TextView dirCount;

    private File currentDir;
    private int maxCount;

    private List<FolderBean> folderBeans = new ArrayList<>();

    private ProgressDialog progressDialog;
    private ImageAdapter imageAdapter;
    private ListImageDirPopupWindow dirPopupWindow;

    private static final int DATA_LOAD_DONE = 0x110;

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(msg.what == DATA_LOAD_DONE){
                progressDialog.dismiss();
                //绑定数据到View中
                dataToView();
                initDirPopupWindow();
                return true;
            }
            return false;
        }
    });

    private void initDirPopupWindow() {
        dirPopupWindow = new ListImageDirPopupWindow(this, folderBeans);
        dirPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                lightBackWindow(true);  //内容区域变亮
            }
        });
        dirPopupWindow.setOnDirSelectedListener(new ListImageDirPopupWindow.OnDirSelectedListener() {
            @Override
            public void onSelected(FolderBean folderBean) {
                currentDir = new File(folderBean.getDir());
                images = Arrays.asList(currentDir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        if(filename.endsWith(".jpg")||filename.endsWith(".jpeg")||filename.endsWith(".png"))
                            return true;
                        return false;
                    }
                }));
                imageAdapter = new ImageAdapter(MainActivity.this, images, currentDir.getAbsolutePath());
                gridView.setAdapter(imageAdapter);
                dirCount.setText(images.size()+"");
                dirName.setText(folderBean.getName());
                dirPopupWindow.dismiss();
            }
        });
    }

    private void lightBackWindow(boolean on) {
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.alpha = on?1.0f:0.3f;
        getWindow().setAttributes(layoutParams);
    }

    private void dataToView() {
        if(currentDir==null){
            Toast.makeText(this, "未扫描到任何图片！", Toast.LENGTH_SHORT).show();
            return;
        }
        images = Arrays.asList(currentDir.list());
        imageAdapter = new ImageAdapter(this, images, currentDir.getAbsolutePath());
        gridView.setAdapter(imageAdapter);
        dirCount.setText(maxCount + "");
        dirName.setText(currentDir.getName());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_main);

        initView();
        initData();
        initEvents();
    }

    private void initEvents() {
        bottomLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dirPopupWindow.setAnimationStyle(R.style.PopupWindowAnim);
                dirPopupWindow.showAsDropDown(bottomLayout, 0, 0);
                lightBackWindow(false);//n内容变暗
            }
        });
    }

    //利用contentProvider扫描手机中所有图片
    private void initData() {
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            Toast.makeText(this, "当前的存储卡不能用!", Toast.LENGTH_SHORT).show();
            return;
        }
        progressDialog = ProgressDialog.show(this, null, "正在加载...");

        new Thread(){
            @Override
            public void run() {
                Uri imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver contentResolver = MainActivity.this.getContentResolver();
                Cursor cursor = contentResolver.query(imageUri, null, MediaStore.Images.Media.MIME_TYPE + " = ? or " +
                        MediaStore.Images.Media.MIME_TYPE + " = ?", new String[]
                        {"image/jpeg", "image/png"}, MediaStore.Images.Media.DATE_MODIFIED);
                Set<String> dirPaths = new HashSet<>();
                while(cursor.moveToNext()){
                    String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                    File parentFile = new File(path).getParentFile();
                    if(parentFile == null)
                        continue;
                    String dirPath = parentFile.getAbsolutePath();
                    FolderBean folderBean;
                    if(dirPaths.contains(dirPath))
                        continue;
                    else {
                        dirPaths.add(dirPath);
                        folderBean = new FolderBean();
                        folderBean.setDir(dirPath);
                        folderBean.setFirstImagePath(path);
                    }

                    if(parentFile.list()==null)
                        continue;
                    int imageCount = parentFile.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            if(filename.endsWith(".jpg")||filename.endsWith(".jpeg")||filename.endsWith(".png"))
                                return true;
                            return false;
                        }
                    }).length;
                    folderBean.setCount(imageCount);
                    folderBeans.add(folderBean);
                    if(imageCount>maxCount){
                        maxCount = imageCount;
                        currentDir = parentFile;
                    }
                }
                cursor.close();
                //扫描完成，释放临时变量的内存
                //dirPaths = null;
                handler.sendEmptyMessage(DATA_LOAD_DONE);    //通知handler扫描完成
            }
        }.start();
    }

    private void initView() {
        gridView = (GridView) findViewById(R.id.grid_view);
        bottomLayout = (RelativeLayout) findViewById(R.id.bottom_layout);
        dirName = (TextView) findViewById(R.id.dir_name);
        dirCount = (TextView) findViewById(R.id.dir_count);
    }

}

class ImageAdapter extends BaseAdapter{

    private static Set<String> selectedImages = new HashSet<>();

    private String dirPath;
    private List<String> imagePaths;
    private LayoutInflater inflater;

    public ImageAdapter(Context context, List<String> data, String dirPath) {
        this.dirPath = dirPath;
        this.imagePaths = data;
        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return imagePaths.size();
    }

    @Override
    public Object getItem(int position) {
        return imagePaths.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.image = (ImageView) convertView.findViewById(R.id.item_image);
            viewHolder.buttonSelect = (ImageButton) convertView.findViewById(R.id.item_select);
            convertView.setTag(viewHolder);
        }else{
            viewHolder = (ViewHolder) convertView.getTag();
        }

        //重置状态
        viewHolder.image.setImageResource(R.drawable.pictures_no);
        viewHolder.buttonSelect.setImageResource(R.drawable.btn_check_off);

        ImageLoader.getInstance(3, ImageLoader.Type.LIFO).loadImage(dirPath + "/" + imagePaths.get(position),
                viewHolder.image);
        final String filePath = dirPath+"/"+imagePaths.get(position);
        viewHolder.image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(selectedImages.contains(filePath)){
                    viewHolder.image.setColorFilter(null);
                    viewHolder.buttonSelect.setImageResource(R.drawable.btn_check_off);
                    selectedImages.remove(filePath);
                }else{
                    selectedImages.add(filePath);
                    viewHolder.image.setColorFilter(Color.parseColor("#77000000"));
                    viewHolder.buttonSelect.setImageResource(R.drawable.btn_check_on);
                }
                //notifyDataSetChanged();
            }
        });

        if(selectedImages.contains(filePath)){
            viewHolder.image.setColorFilter(Color.parseColor("#77000000"));
            viewHolder.buttonSelect.setImageResource(R.drawable.btn_check_on);
        }
        return convertView;
    }

    private class ViewHolder {

        ImageView image;
        ImageButton buttonSelect;
    }
}
