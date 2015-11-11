package edu.cuc.stephen.imageloader;

import android.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

import edu.cuc.stephen.imageloader.util.ImageLoader;

/**
 * Created by stephen on 15-10-16.
 */
public class ListImageDirPopupWindow extends PopupWindow {

    private int width;
    private int height;

    public interface OnDirSelectedListener{
        void onSelected(FolderBean folderBean);
    }

    public OnDirSelectedListener listener;

    public void setOnDirSelectedListener(OnDirSelectedListener listener) {
        this.listener = listener;
    }

    public ListImageDirPopupWindow(Context context, List<FolderBean> data) {
        calcWidthHeight(context);
        convertView = LayoutInflater.from(context).inflate(R.layout.popup_main, null);
        this.data = data;
        setContentView(convertView);
        setWidth(width);
        setHeight(height);
        setTouchable(true);
        setFocusable(true);
        setOutsideTouchable(true);
        setBackgroundDrawable(new BitmapDrawable());
        setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    dismiss();
                    return true;
                }
                return false;
            }
        });

        initViews(context);
        initEvents();
    }

    private void initEvents() {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(listener!=null){
                    listener.onSelected(data.get(position));
                }
            }
        });
    }

    private void initViews(Context context) {
        listView = (ListView) convertView.findViewById(R.id.list_dir);
        listView.setAdapter(new ListDirAdapter(context, data));
    }

    private class ListDirAdapter extends ArrayAdapter<FolderBean>{

        private LayoutInflater inflater;
        public ListDirAdapter(Context context, List<FolderBean> objects) {
            super(context, 0, objects);
            inflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if(convertView == null){
                holder = new ViewHolder();
                convertView = inflater.inflate(R.layout.popup_item, parent, false);
                holder.image = (ImageView) convertView.findViewById(R.id.dir_item_image);
                holder.dirName = (TextView) convertView.findViewById(R.id.dir_item_name);
                holder.dirCount = (TextView) convertView.findViewById(R.id.dir_item_count);
                convertView.setTag(holder);
            }else{
                holder = (ViewHolder) convertView.getTag();
            }
            FolderBean bean = getItem(position);
            holder.image.setImageResource(R.drawable.pictures_no);  //先重置
            ImageLoader.getInstance().loadImage(bean.getFirstImagePath(), holder.image);
            holder.dirCount.setText(bean.getCount()+"");
            holder.dirName.setText(bean.getName());
            //convertView.setTag(holder);
            return convertView;
        }

        private class ViewHolder{
            ImageView image;
            TextView dirName;
            TextView dirCount;
        }

    }

    private void calcWidthHeight(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        width = displayMetrics.widthPixels;
        height = displayMetrics.heightPixels*7/10;
    }

    private View convertView;
    private ListView listView;
    private List<FolderBean> data;
}
