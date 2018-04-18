package xyz.rty813.queryexamschedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class MainActivity extends AppCompatActivity {
    private EditText editun;
    private EditText editpw;
    private ListView listView;
    private String username;
    private String password;
    private List<Map<String, String>> lvList;
    private SimpleAdapter simpleAdapter;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        editpw = findViewById(R.id.editpw);
        editun = findViewById(R.id.editun);
        listView = findViewById(R.id.listView);
        lvList = new ArrayList<>();
        progressBar = findViewById(R.id.progressBar);
        simpleAdapter = new SimpleAdapter(MainActivity.this, lvList, android.R.layout.simple_list_item_2,
                new String[]{"name", "detail"}, new int[]{android.R.id.text1, android.R.id.text2});
        listView.setAdapter(simpleAdapter);
        getUserInfo();
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressBar.setVisibility(View.VISIBLE);
                lvList.removeAll(lvList);
                simpleAdapter.notifyDataSetChanged();
                username = editun.getText().toString();
                password = editpw.getText().toString();
                saveUserInfo(username, password);
                executorService.execute(new QueryExamScheduleThread());
            }
        });
    }

    public void saveUserInfo(String username, String password){
        SharedPreferences preferences = getSharedPreferences("user", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("username", username);
        editor.putString("password", password);
        editor.apply();
    }

    private void getUserInfo(){
        SharedPreferences preferences = getSharedPreferences("user", Context.MODE_PRIVATE);
        editun.setText(preferences.getString("username", null));
        editpw.setText(preferences.getString("password", null));
    }

    private class QueryExamScheduleThread implements Runnable {

        @Override
        public void run() {
            try {
                String param = "username=" + username + "&password=" + password;
                URL url = new URL("http://us.nwpu.edu.cn/eams/login.action");

                //获取Cookie
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.87 Safari/537.36 OPR/42.0.2393.94 (Edition Baidu)");
                String cookie = connection.getHeaderField("Set-Cookie");
                System.out.println(connection.getHeaderFields());

                //模拟登陆
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Cookie", cookie);

                OutputStream os = connection.getOutputStream();
                os.write(param.getBytes("GBK"));
                os.close();

                //并不知道为什么加这个，但是不加这个下面就无法正常获取数据
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                br.close();

                //获取成绩
                url = new URL("http://us.nwpu.edu.cn/eams/stdExamTable!examTable.action?examBatch.id=321");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Cookie", cookie);

                //获取网页源代码
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line + '\n');
                }
                reader.close();

                //利用JSoup解析
                Document document = Jsoup.parse(builder.toString());
                Elements trTags = document.select("tr");
                for (int i = 1; i < trTags.size(); i++) {
                    Elements tdTags = trTags.get(i).select("td");
                    if (tdTags.size() > 7) {
                        if (tdTags.get(3).text().contains("not")) {
                            continue;
                        }
                        Map<String, String> map = new HashMap<>();
                        map.put("name", tdTags.get(1).text());
                        map.put("detail",
                                tdTags.get(3).text() + " " +
                                        tdTags.get(4).text() + " " +
                                        tdTags.get(7).text());
                        lvList.add(map);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        simpleAdapter.notifyDataSetChanged();
                        progressBar.setVisibility(View.GONE);
                    }
                });
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
