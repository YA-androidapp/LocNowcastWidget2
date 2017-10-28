package jp.gr.java_conf.ya.locnowcastwidget2;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocNowcastWidgetService extends Service implements LocationListener, GpsStatus.Listener {
    private boolean notPositioning = true;
    private double currentLatitude = -91;
    private double currentLongitude = -181;
    private final int nowcastDataNum = 11;
    private final int radameDataNum = 6;
    private final long freqSec = 30 * 1000;
    private int icon = R.drawable.icon512;
    private Intent buttonIntent;
    private Location preLocation;
    private LocationManager locationManager;
    private final Pattern colorCodePattern = Pattern.compile("^#[0-9A-Fa-f]{6,8}$");
    private RemoteViews remoteViews;
    private SharedPreferences pref_app;
    private SpannableString preText = new SpannableString("");
    private final String BUTTON_CLICK_ACTION = "BUTTON_CLICK_ACTION";
    private String preStatus = "";
    private String preGpsStatus = "";

    private String reverseGeocoding(String lat, String lng) {
        return getJsonFromWeb("http://www.finds.jp/ws/rgeocode.php?json&lat=" + lat + "&lon=" + lng);
    }

    private String getJsonFromWeb(String uri) {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

        pref_app = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean pref_view_footer_revgeocoding_p = pref_app.getBoolean("pref_view_footer_revgeocoding_p", false);
        final boolean pref_view_footer_revgeocoding_m = pref_app.getBoolean("pref_view_footer_revgeocoding_m", true);
        final boolean pref_view_footer_revgeocoding_s = pref_app.getBoolean("pref_view_footer_revgeocoding_s", true);

        try {
            URLConnection conn = new URL(uri).openConnection();
            ((HttpURLConnection) conn).setRequestMethod("GET");
            conn.connect();
            InputStream is = conn.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is);
            ByteArrayOutputStream responseArray = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int length;
            while ((length = bis.read(buff)) != -1) {
                if (length > 0) {
                    responseArray.write(buff, 0, length);
                }
            }
            bis.close();
            is.close();

            JSONObject root = new JSONObject(new String(responseArray.toByteArray()));
            JSONObject result = root.getJSONObject("result");

            String section = "";
            if (pref_view_footer_revgeocoding_s) {
                JSONArray local = result.getJSONArray("local");
                //			for (int i = 0; i < local.length(); i++) {
                //				JSONObject l = local.getJSONObject(i);
                //				section = l.getString("section");
                if (local.length() > 0) {
                    JSONObject l = local.getJSONObject(0);
                    section = l.getString("section");
                }
                //				log("section[" + i + "] : " + section);
                //			}
            }

            String pname = "";
            if (pref_view_footer_revgeocoding_p) {
                JSONObject prefecture = result.getJSONObject("prefecture");
                pname = prefecture.getString("pname");
                log("pname : " + pname);
            }

            String mname = "";
            if (pref_view_footer_revgeocoding_m) {
                JSONObject municipality = result.getJSONObject("municipality");
                mname = municipality.getString("mname");
                log("mname : " + mname);
            }

            return (pname + mname + section).replaceAll("[ 　]", "");
        } catch (IOException e) {
            log(e.toString() + " : " + e.getMessage());
        } catch (Exception e) {
            log(e.toString() + " : " + e.getMessage());
        }

        return "";
    }

    private String getFromWeb(double lat, double lon) {
        String uri = "https://www.jma.go.jp/jp/highresorad/?"
                + "jmamap.AMD_RAIN10M=false&jmamap.animationSpeed=5&jmamap.circle=false"
                + "&jmamap.control=false&jmamap.height=500&jmamap.highresorad.switchtype.MOVE_RAIN=MOVE"
                + "&jmamap.highresorad.switchtype.TPNC_KMNC=TPNC1_KMNC2&jmamap.HRKSNC=true"
                + "&jmamap.HRKSNC_GRAY=false&jmamap.HRKSNC_NONE=false&jmamap.KMNC=false"
                + "&jmamap.legend=false&jmamap.LIDEN=false&jmamap.MOVE=false&jmamap.MOVE_SEVERE=false"
                + "&jmamap.MOVE_SLIGHTLY_HEAVY=false&jmamap.MUNICIPALITY=false&jmamap.RAILROAD=false"
                + "&jmamap.RIVER=false&jmamap.ROAD=false&jmamap.TPNC=false&jmamap.TPNC1_KMNC2=false"
                + "&jmamap.TPNC2_KMNC4=false&jmamap.width=600&jmamap.zoom=9&jmamap.centerLat="
                + Double.toString(lat)
                + "&jmamap.centerLon="
                + Double.toString(lon);

        try {
            Document doc = Jsoup.connect(uri).get();
            Elements newsHeadlines = doc.select("div.jmamesh-map");

            if (!newsHeadlines.isEmpty()) {
                return newsHeadlines.html();
            }
        } catch (IOException e) {
        }

        return "";
    }

    private Bitmap getBitmapFromWeb(String uri) {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }

        Bitmap bmp = null;
        try {
            URLConnection conn = new URL(uri).openConnection();
            conn.connect();
            InputStream is = conn.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is);
            bmp = BitmapFactory.decodeStream(bis);
            bis.close();
            is.close();
        } catch (IOException e) {
            log(e.toString() + " : " + e.getMessage());
            bmp = null;
        } catch (Exception e) {
            log(e.toString() + " : " + e.getMessage());
            bmp = null;
        }

        return bmp;
    }


    private void getWeather(double lat, double lng) {
        pref_app = PreferenceManager.getDefaultSharedPreferences(this);

        long lastUpdateTime;
        try {
            lastUpdateTime = Long.parseLong(pref_app.getString("last_update_time", "0"));
        } catch (NumberFormatException e1) {
            lastUpdateTime = 0;
        }

        //		if (lastUpdateTime < 0) {
        //			lastUpdateTime = 0;
        //		}

        final long currentTime = System.currentTimeMillis();

        if (currentTime - lastUpdateTime > freqSec) {
            log("getWeather( " + Double.toString(lat) + " , " + Double.toString(lng) + " )");

            if ((lat < -90) || (lat > 90) || (lng < -180) || (lng > 180)) {
                try {
                    lat = Double.parseDouble(pref_app.getString("pref_lat", "35.681382"));
                } catch (NumberFormatException e) {
                    lat = 35.681382;
                }
                try {
                    lng = Double.parseDouble(pref_app.getString("pref_long", "139.766084"));
                } catch (NumberFormatException e) {
                    lng = 139.766084;
                }
            }

            final double latitude = lat;
            final double longitude = lng;

            final boolean pref_source_weatherreport = pref_app.getBoolean("pref_source_weatherreport", false);
            final boolean pref_use_revgeocoding = pref_app.getBoolean("pref_use_revgeocoding", false);
            final boolean pref_use_radame = pref_source_weatherreport ? false : pref_app.getBoolean("pref_use_radame", false);
            final boolean pref_view_footer_gettime = pref_app.getBoolean("pref_view_footer_gettime", true);
            int pref_latlong_decimal;
            try {
                pref_latlong_decimal = Integer.parseInt(pref_app.getString("pref_latlong_decimal", "3"));
            } catch (Exception e) {
                pref_latlong_decimal = 3;
            }
            final String pref_latlong_decimal_string = Integer.toString(pref_latlong_decimal);
            final String pref_bgcolor = pref_app.getString("pref_bgcolor", "");
            final String pref_fontcolor_footer_start = pref_app.getString("pref_fontcolor_footer_start", "#0000ff");
            final String pref_fontcolor_footer_error = pref_app.getString("pref_fontcolor_footer_error", "#ff0000");
            final String pref_fontcolor_footer_latlong = pref_app.getString("pref_fontcolor_footer_latlong", "#666666");
            final boolean pref_notification_onreload = pref_app.getBoolean("pref_notification_onreload", false);

            new Thread(new Runnable() {
                public void run() {

                    updateImageview(BitmapFactory.decodeResource(getResources(), icon));
                    updateTextview(Html.fromHtml(getString(R.string.now_loading) + " <small>(" + (notPositioning ? "X" : "O") + ")</small>..."));

                    final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm", Locale.JAPAN);
                    final SimpleDateFormat sdf_s = new SimpleDateFormat("HH:mm", Locale.JAPAN);

                    Calendar cal0 = Calendar.getInstance();
                    cal0.setLenient(true);
                    cal0.set(Calendar.MINUTE, (cal0.get(Calendar.MINUTE) + -5 + (-1) * ((cal0.get(Calendar.MINUTE) % 5))));
                    Date time0 = cal0.getTime();
                    Calendar cal = cal0;
                    cal.setLenient(true);
                    cal.add(Calendar.MINUTE, 5);

                    Calendar cal01 = Calendar.getInstance();
                    cal01.setLenient(true);
                    cal01.set(Calendar.MINUTE, (cal01.get(Calendar.MINUTE) + -30 + (-1) * ((cal01.get(Calendar.MINUTE) % 30))));
                    Date time01 = cal01.getTime();
                    Calendar cal1 = cal01;
                    cal1.setLenient(true);

                    String error = "";
                    String start = "";
                    String start_noti = "";

                    int[] precipitations = new int[nowcastDataNum + (pref_use_radame ? radameDataNum : 0)];
                    StringBuilder sb = new StringBuilder();
                    StringBuilder sb_noti = new StringBuilder();

                    for (int i = 0; i < nowcastDataNum; i++) {
                        updateTextview(Html.fromHtml(getString(R.string.now_loading) + " <small>(" + (notPositioning ? "X" : "O") + ")</small>...<br>" + progressString(2 * i, 2 * nowcastDataNum)));

                        cal.setLenient(true);
                        cal.add(Calendar.MINUTE, 5);

                        // TODO
                        Bitmap bmp = getBitmapFromWeb("TODO");

                        updateTextview(Html.fromHtml(getString(R.string.now_loading) + " <small>(" + (notPositioning ? "X" : "O") + ")</small>...<br>"
                                + progressString(2 * i + 1, 2 * nowcastDataNum)));

                        if (bmp == null) {
                            precipitations[i] = -1;

                            sb.append("* ");
                            sb_noti.append("* ");
                            error = "<br /> <font color=\"" + pref_fontcolor_footer_error + "\">" + getString(R.string.bmp_null) + " (" + Integer.toString(i) + ")" + "</font>";
                        } else {
                            int[] ninePixels = getNinePixels(bmp, 0, 0); // TODO
                            if (ninePixels == null) {
                                precipitations[i] = -1;

                                sb.append("* ");
                                sb_noti.append("* ");
                                error = "<br /> <font color=\"" + pref_fontcolor_footer_error + "\">" + getString(R.string.ninepixels_null) + " (" + Integer.toString(i) + ")" + "</font>";
                            } else {
                                int precipitation = getMaxPrecipitation(ninePixels);
                                precipitations[i] = precipitation;

                                if (precipitation > -1) {
                                    if (start.equals("")) {
                                        if (cal.after(Calendar.getInstance())) {
                                            start = "<font color=\"" + pref_fontcolor_footer_start + "\">降り始め予想時刻:" + sdf_s.format(cal.getTime()) + "</font><br />";
                                            start_noti = sdf_s.format(cal.getTime()) + "降り始め";
                                        } else {
                                            start = "<font color=\"" + pref_fontcolor_footer_start + "\">降り始め直前または既に降っています</font><br />";
                                            start_noti = "直前";
                                        }
                                    }

                                    sb.append("<font color=\"" + precipitationToColorcode(precipitation, true) + "\">");
                                    sb.append(precipitation);
                                    sb.append("</font>");
                                    sb.append(" ");
                                    sb_noti.append(precipitation);
                                    sb_noti.append(" ");
                                } else {
                                    sb.append("- ");
                                    sb_noti.append("- ");
                                }
                            }
                        }
                    }

                    String latlngPart = "";
                    if (pref_use_revgeocoding) {
                        latlngPart = reverseGeocoding(Double.toString(latitude), Double.toString(longitude));
                    } else {
                        latlngPart = String.format("%." + pref_latlong_decimal_string + "f", latitude) + "," + String.format("%." + pref_latlong_decimal_string + "f", longitude);
                    }

                    String gettimePart = "";
                    String gettimePart_noti = "";
                    if (pref_view_footer_gettime) {
                        gettimePart = "<br /><small> 取得時刻: " + sdf_s.format(new Date(System.currentTimeMillis())) + "</small>";
                        gettimePart_noti = sdf_s.format(new Date(System.currentTimeMillis())) + "取得";
                    }

                    SpannableString spannable =
                            new SpannableString(Html.fromHtml(start + sb.toString() + "<br /> <font color=\"" + pref_fontcolor_footer_latlong + "\">" + latlngPart + " <small>"
                                    + (notPositioning ? "X" : "O") + "</small></font>" + gettimePart + error));
                    if (pref_bgcolor.equals("") == false) {
                        BackgroundColorSpan bgcolor = new BackgroundColorSpan(Color.parseColor(pref_bgcolor));
                        spannable.setSpan(bgcolor, 0, spannable.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                    }

                    if (error.equals("")) {
                        Bitmap bitmap = drawPrecipitationsIcon(precipitations);
                        updateImageview(bitmap);

                        if (pref_notification_onreload) {
                            notification(start_noti + " " + gettimePart_noti, sb_noti.toString(), bitmap); // latlngPart
                        }
                    } else {
                        updateImageview(BitmapFactory.decodeResource(getResources(), icon));
                    }
                    updateTextview(spannable);
                    preText = spannable;
                }
            }).start();

            try {
                SharedPreferences.Editor editor = pref_app.edit();
                editor.putString("last_update_time", Long.toString(currentTime));
                editor.commit();
            } catch (Exception e) {
            }

        } else {
            log("frequency");
            updateImageview(BitmapFactory.decodeResource(getResources(), icon));
            updateTextview(preText);

            Random rnd = new Random();
            if (0 == rnd.nextInt(10)) {
                initLocationManager();
            }
        }

    }

    private void notification(String title, String summary, Bitmap bitmap) {
        int notificationId = 0;

        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        Notification.Builder notificationBuilder = new Notification.Builder(this) //
                .setAutoCancel(true) //
                .setSmallIcon(icon) //
                .setContentTitle(title) //
                .setContentText(summary) //
                .setContentIntent(pendingIntent) //
                // .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS)
                .setStyle(new Notification.BigPictureStyle().bigPicture(bitmap).setBigContentTitle(title).setSummaryText(summary)) // 16
                ;
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notificationBuilder.build()); // 16
        // notificationManager.notify(notificationId, notificationBuilder.getNotification());
    }

    private Bitmap drawPrecipitationsIcon(int[] precipitations) {
        pref_app = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean pref_source_weatherreport = pref_app.getBoolean("pref_source_weatherreport", false);
        final boolean pref_use_radame = pref_source_weatherreport ? false : pref_app.getBoolean("pref_use_radame", false);
        int pref_icon_dif;
        try {
            pref_icon_dif = Integer.parseInt(pref_app.getString("pref_icon_dif", "10"));
        } catch (NumberFormatException e) {
            pref_icon_dif = 10;
        }

        if (pref_icon_dif <= 0) {
            pref_icon_dif = 1;
        }

        Bitmap sun = BitmapFactory.decodeResource(getResources(), R.drawable.sun);
        Bitmap umb = BitmapFactory.decodeResource(getResources(), R.drawable.umb);

        Bitmap bitmap =
                Bitmap.createBitmap((nowcastDataNum + radameDataNum) * (pref_icon_dif * 2) + umb.getWidth(), (nowcastDataNum + radameDataNum) * (pref_icon_dif * 2) + umb.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);

        if (pref_use_radame) {
            Bitmap sunr = BitmapFactory.decodeResource(getResources(), R.drawable.sunr);
            Bitmap umbr = BitmapFactory.decodeResource(getResources(), R.drawable.umbr);
            for (int i = radameDataNum - 1; i >= 0; i--) {
                Paint paint = new Paint();
                if (precipitations[nowcastDataNum + i] > -1) {
                    LightingColorFilter lightingColorFilter = new LightingColorFilter(Color.parseColor(precipitationToColorcode(precipitations[nowcastDataNum + i], false)), 0);
                    paint.setFilterBitmap(true);
                    paint.setColorFilter(lightingColorFilter);
                    canvas.drawBitmap(umbr, (nowcastDataNum + i) * (pref_icon_dif * 2), (nowcastDataNum + i) * pref_icon_dif, paint);
                } else {
                    canvas.drawBitmap(sunr, (nowcastDataNum + i) * (pref_icon_dif * 2), (nowcastDataNum + i) * pref_icon_dif, paint);
                }
            }
        }

        for (int i = nowcastDataNum - 1; i >= 0; i--) {
            Paint paint = new Paint();
            if (precipitations[i] > -1) {
                LightingColorFilter lightingColorFilter = new LightingColorFilter(Color.parseColor(precipitationToColorcode(precipitations[i], false)), 0);
                paint.setFilterBitmap(true);
                paint.setColorFilter(lightingColorFilter);
                canvas.drawBitmap(umb, i * (pref_icon_dif * 2), i * pref_icon_dif, paint);
            } else {
                canvas.drawBitmap(sun, i * (pref_icon_dif * 2), i * pref_icon_dif, paint);
            }
        }

        return bitmap;
    }

    private int getMaxPrecipitation(int[] colors) {
        int maxPrecipitation = -1;
        for (int color : colors) {
            int precipitation = colorToPrecipitations(color);
            if (precipitation > maxPrecipitation) {
                maxPrecipitation = precipitation;
            }
        }
        return maxPrecipitation;
    }

    private int[] getNinePixels(Bitmap bmp, int x, int y) {
        if (bmp == null) {
            return null;
        }

        int[] ninePixels = new int[9];

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                try {
                    ninePixels[3 * i + j] = bmp.getPixel(x + (j - 1), y + (i - 1));
                } catch (Exception e) {
                    ninePixels[3 * i + j] = -1;
                }
            }
        }

        return ninePixels;
    }

    private String precipitationToColorcode(int precipitation, boolean string) {
        pref_app = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean pref_source_weatherreport = pref_app.getBoolean("pref_source_weatherreport", false);

        String colorCode = "#000000";
        if (pref_source_weatherreport) {

            final String pref_fontcolor_colorcode_wr_100 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_100", "#660000");
            final String pref_fontcolor_colorcode_wr_80 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_80", "#CC0000");
            final String pref_fontcolor_colorcode_wr_60 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_60", "#FF6666");
            final String pref_fontcolor_colorcode_wr_40 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_40", "#FF99FF");
            final String pref_fontcolor_colorcode_wr_30 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_30", "#FFCC33");
            final String pref_fontcolor_colorcode_wr_20 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_20", "#FFFF99");
            final String pref_fontcolor_colorcode_wr_10 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_10", "#33FF99");
            final String pref_fontcolor_colorcode_wr_5 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_5", "#0099FF");
            final String pref_fontcolor_colorcode_wr_1 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_1", "#66CCFF");
            final String pref_fontcolor_colorcode_wr_0 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_wr_0", "#CCFFFF");
            final String pref_fontcolor_colorcode_minus1 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_minus1", "#000000");

            colorCode = pref_fontcolor_colorcode_minus1;
            if (precipitation == 100) {
                colorCode = pref_fontcolor_colorcode_wr_100;
            } else if (precipitation == 80) {
                colorCode = pref_fontcolor_colorcode_wr_80;
            } else if (precipitation == 60) {
                colorCode = pref_fontcolor_colorcode_wr_60;
            } else if (precipitation == 40) {
                colorCode = pref_fontcolor_colorcode_wr_40;
            } else if (precipitation == 30) {
                colorCode = pref_fontcolor_colorcode_wr_30;
            } else if (precipitation == 20) {
                colorCode = pref_fontcolor_colorcode_wr_20;
            } else if (precipitation == 10) {
                colorCode = pref_fontcolor_colorcode_wr_10;
            } else if (precipitation == 5) {
                colorCode = pref_fontcolor_colorcode_wr_5;
            } else if (precipitation == 1) {
                colorCode = pref_fontcolor_colorcode_wr_1;
            } else if (precipitation == 0) {
                colorCode = pref_fontcolor_colorcode_wr_0;
            }

        } else {

            final String pref_fontcolor_colorcode_80 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_80", "#b40068");
            final String pref_fontcolor_colorcode_50 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_50", "#ff2800");
            final String pref_fontcolor_colorcode_30 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_30", "#ff9900");
            final String pref_fontcolor_colorcode_20 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_20", "#faf500");
            final String pref_fontcolor_colorcode_10 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_10", "#0041ff");
            final String pref_fontcolor_colorcode_5 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_5", "#218cff");
            final String pref_fontcolor_colorcode_1 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_1", "#c0ffff");
            final String pref_fontcolor_colorcode_0 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_0", "#d2d2d2");
            final String pref_fontcolor_colorcode_minus1 = pref_app.getString("pref_" + (string ? "font" : "icon") + "color_colorcode_minus1", "#000000");

            colorCode = pref_fontcolor_colorcode_minus1;
            if (precipitation == 80) {
                colorCode = pref_fontcolor_colorcode_80;
            } else if (precipitation == 50) {
                colorCode = pref_fontcolor_colorcode_50;
            } else if (precipitation == 30) {
                colorCode = pref_fontcolor_colorcode_30;
            } else if (precipitation == 20) {
                colorCode = pref_fontcolor_colorcode_20;
            } else if (precipitation == 10) {
                colorCode = pref_fontcolor_colorcode_10;
            } else if (precipitation == 5) {
                colorCode = pref_fontcolor_colorcode_5;
            } else if (precipitation == 1) {
                colorCode = pref_fontcolor_colorcode_1;
            } else if (precipitation == 0) {
                colorCode = pref_fontcolor_colorcode_0;
            }
        }

        Matcher m = colorCodePattern.matcher(colorCode);

        if (m.find()) {
            return colorCode;
        } else {
            final String[] colorCodes =
                    {"red", "blue", "green", "black", "white", "gray", "cyan", "magenta", "yellow", "lightgray", "darkgray", "grey", "lightgrey", "darkgrey", "aqua", "fuschia", "lime", "maroon",
                            "navy", "olive", "purple", "silver", "teal"};
            for (String cc : colorCodes) {
                if (colorCode.equals(cc)) {
                    return colorCode;
                }
            }
        }

        return "#000000";
    }

    private int colorToPrecipitations(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        pref_app = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean pref_source_weatherreport = pref_app.getBoolean("pref_source_weatherreport", false);

        if (pref_source_weatherreport) {

            if (red == 102 && green == 0 && blue == 0) {
                return 100;
            } else if (red == 204 && green == 0 && blue == 0) {
                return 80;
            } else if (red == 255 && green == 102 && blue == 102) {
                return 60;
            } else if (red == 255 && green == 153 && blue == 255) {
                return 40;
            } else if (red == 255 && green == 204 && blue == 51) {
                return 30;
            } else if (red == 255 && green == 255 && blue == 153) {
                return 20;
            } else if (red == 51 && green == 255 && blue == 153) {
                return 10;
            } else if (red == 0 && green == 153 && blue == 255) {
                return 5;
            } else if (red == 102 && green == 204 && blue == 255) {
                return 1;
            } else if (red == 204 && green == 255 && blue == 255) {
                return 0;
            }

        } else {

            if (red == 180 && green == 0 && blue == 104) {
                return 80;
            } else if (red == 255 && green == 40 && blue == 0) {
                return 50;
            } else if (red == 255 && green == 153 && blue == 0) {
                return 30;
            } else if (red == 250 && green == 245 && blue == 0) {
                return 20;
            } else if (red == 0 && green == 65 && blue == 255) {
                return 10;
            } else if (red == 33 && green == 140 && blue == 255) {
                return 5;
            } else if (red == 160 && green == 210 && blue == 255) {
                return 1;
            } else if (red == 242 && green == 242 && blue == 255) {
                return 0;
            }

        }

        return -1;
    }

    private void initLocationManager() {
        pref_app = PreferenceManager.getDefaultSharedPreferences(this);
        int pref_mintime;
        try {
            pref_mintime = 1000 * Integer.parseInt(pref_app.getString("pref_mintime", "600"));
        } catch (NumberFormatException e) {
            pref_mintime = -1;
        }
        int pref_mindistance;
        try {
            pref_mindistance = Integer.parseInt(pref_app.getString("pref_mindistance", "10"));
        } catch (NumberFormatException e) {
            pref_mindistance = -1;
        }

        if ((pref_mintime >= 0) && (pref_mindistance >= 0)) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            try {
                locationManager.addGpsStatusListener(this);
            } catch (SecurityException e) {
            }
            locationManager.removeUpdates(this);
            List<String> providers = locationManager.getProviders(true);
            if (providers.size() > 0) {
                for (String provider : providers) {
                    if (provider.equals("passive") == false) {
                        toast("provider:" + provider);
                        try {
                            locationManager.requestLocationUpdates(provider, pref_mintime, pref_mindistance, this);
                        } catch (SecurityException e) {
                        }
                    }
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("onStartCommand()");

        try {
            super.onStartCommand(intent, flags, startId);
        } catch (Exception e) {
        }

        notPositioning = true;
        initLocationManager();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        log("onDestroy()");

        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationManager.removeUpdates(this);
            locationManager.removeGpsStatusListener(this);
        } catch (Exception e) {
        }

        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    // 16
    @Override
    public void onStart(Intent intent, int startId) {
        log("onStart()");

        super.onStart(intent, startId);

        setButtonIntentIfNull();

        if ((AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) || (BUTTON_CLICK_ACTION.equals(intent.getAction()))) {
            log("onStart() BUTTON_CLICK_ACTION");

            updateTextview(Html.fromHtml(getString(R.string.now_loading) + " <small>(" + (notPositioning ? "X" : "O") + ")</small>"));

            pref_app = PreferenceManager.getDefaultSharedPreferences(this);
            String pref_place;
            try {
                pref_place = pref_app.getString("pref_place", "here");
            } catch (Exception e) {
                pref_place = "here";
            }
            if (pref_place.equals("here")) {
                if ((currentLatitude < -90) || (currentLatitude > 90) || (currentLongitude < -180) || (currentLongitude > 180)) {
                    getWeather(currentLatitude, currentLongitude);
                }
            } else if (pref_place.equals("home")) {
                double pref_lat = Double.parseDouble(pref_app.getString("pref_lat", "35.681382"));
                double pref_long = Double.parseDouble(pref_app.getString("pref_long", "139.766084"));
                getWeather(pref_lat, pref_long);
            } else if (pref_place.equals("pre")) {
                double pref_lat = Double.parseDouble(pref_app.getString("pref_pre_lat", "35.681382"));
                double pref_long = Double.parseDouble(pref_app.getString("pref_pre_long", "139.766084"));
                getWeather(pref_lat, pref_long);
            } else {
                double pref_lat = Double.parseDouble(pref_app.getString("pref_lat", "35.681382"));
                double pref_long = Double.parseDouble(pref_app.getString("pref_long", "139.766084"));
                getWeather(pref_lat, pref_long);
            }
        } else {
            setRemoteViews();
            updateRemoteViews();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        final double lat = location.getLatitude();
        final double lng = location.getLongitude();

        toast("onLocationChanged: " + String.valueOf(lat) + " , " + String.valueOf(lng));

        if ((lat >= -90) || (lat <= 90) || (lng >= -180) || (lng <= 180)) {
            // if (notPositioning) {
            notPositioning = false;
            // }
        }

        if (preLocation == null) {
            try {
                preLocation = new Location("dummyprovider");
                preLocation.setLatitude(35.681382);
                preLocation.setLongitude(139.766084);
            } catch (Exception e) {
            }
        }
        preLocation = location;

        SharedPreferences.Editor editor = pref_app.edit();
        editor.putString("pref_pre_lat", Double.toString(currentLatitude));
        editor.putString("pref_pre_long", Double.toString(currentLongitude));
        editor.commit();

        currentLatitude = lat;
        currentLongitude = lng;

        pref_app = PreferenceManager.getDefaultSharedPreferences(this);
        String pref_place;
        try {
            pref_place = pref_app.getString("pref_place", "here");
        } catch (Exception e) {
            pref_place = "here";
        }
        if (pref_app.getBoolean("pref_getweather_onlocationchanged", true)) {
            if (pref_place.equals("here")) {
                getWeather(lat, lng);
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationManager.removeUpdates(this);
            locationManager.removeGpsStatusListener(this);
        } catch (Exception e) {
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int statusInt, Bundle extras) {
        String status = "Unknown";
        if (statusInt == LocationProvider.AVAILABLE) {
            status = "AVAILABLE";
        } else if (statusInt == LocationProvider.OUT_OF_SERVICE) {
            status = "OUT OF SERVICE";
        } else if (statusInt == LocationProvider.TEMPORARILY_UNAVAILABLE) {
            status = "TEMP UNAVAILABLE";
        }

        if (status.equals(preStatus) == false) {
            toast("onStatusChanged: status: " + status);
            preStatus = status;
        }
    }

    @Override
    public void onGpsStatusChanged(int event) {
        String status = "";
        if (event == GpsStatus.GPS_EVENT_FIRST_FIX) {
            status = "FIRST FIX";
        } else if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            status = "SATELLITE STATUS";
        } else if (event == GpsStatus.GPS_EVENT_STARTED) {
            status = "STARTED";
        } else if (event == GpsStatus.GPS_EVENT_STOPPED) {
            status = "STOPPED";
        }

        if (status.equals(preGpsStatus) == false) {
            toast("onGpsStatusChanged: status: " + status);
            preGpsStatus = status;
        }
    }

    private void toast(final String str) {
        //		log(str);
        //
        //		try {
        //			final Handler handler = new Handler();
        //			handler.post(new Runnable() {
        //				@Override
        //				public void run() {
        //					Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
        //					return;
        //				}
        //			});
        //		} catch (Exception e) {
        //		}
    }

    private void log(String str) {
        Log.v("LocNowcastWidget", str);
    }

    private void setButtonIntentIfNull() {
        try {
            if (buttonIntent == null) {
                buttonIntent = new Intent();
                buttonIntent.setAction(BUTTON_CLICK_ACTION);
            }
        } catch (Exception e) {
        }
    }

    private void setRemoteViews() {
        pref_app = PreferenceManager.getDefaultSharedPreferences(this);
        int pref_fontsize;
        try {
            pref_fontsize = Integer.parseInt(pref_app.getString("pref_fontsize", "12"));
        } catch (NumberFormatException e) {
            pref_fontsize = 12;
        }

        if (pref_fontsize <= 0) {
            pref_fontsize = 12;
        }

        PendingIntent pendingIntent = PendingIntent.getService(this, 0, buttonIntent, 0);
        remoteViews = new RemoteViews(getPackageName(), R.layout.main);
        remoteViews.setFloat(R.id.textView1, "setTextSize", pref_fontsize);
        remoteViews.setFloat(R.id.textView2, "setTextSize", pref_fontsize);
        remoteViews.setOnClickPendingIntent(R.id.imageView1, pendingIntent);
        remoteViews.setOnClickPendingIntent(R.id.textView1, pendingIntent);
        remoteViews.setOnClickPendingIntent(R.id.textView2, pendingIntent);
    }

    public String progressString(int num, int max) {
        return "|" + repeatString("=", num) + ">>" + repeatString("_", max - num) + "|";
    }

    public String repeatString(String str, int num) {
        return new String(new char[num]).replace("\0", str);
    }

    public void updateTextview(CharSequence string) {
        pref_app = PreferenceManager.getDefaultSharedPreferences(this);
        boolean pref_gravity = pref_app.getBoolean("pref_gravity", false);

        setRemoteViews();

        if (pref_gravity) {
            remoteViews.setViewVisibility(R.id.textView1, View.INVISIBLE);
            remoteViews.setViewVisibility(R.id.textView2, View.VISIBLE);
            remoteViews.setTextViewText(R.id.textView2, string);
        } else {
            remoteViews.setViewVisibility(R.id.textView1, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.textView2, View.INVISIBLE);
            remoteViews.setTextViewText(R.id.textView1, string);
        }

        updateRemoteViews();
    }

    public void updateImageview(Bitmap bitmap) {
        setRemoteViews();
        //	URL neturl = new URL(url);
        //	Drawable drawable = Drawable.createFromStream(neturl.openStream(), "src");
        //	Bitmap bitmap = ( (BitmapDrawable) drawable ).getBitmap();
        remoteViews.setImageViewBitmap(R.id.imageView1, bitmap);
        updateRemoteViews();
    }

    public void updateRemoteViews() {
        final ComponentName componentName = new ComponentName(this, LocNowcastWidget.class);
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetManager.updateAppWidget(componentName, remoteViews);
    }
}

// <!-- Copyright 2014 (c) YA <ya.androidapp@gmail.com> All rights reserved. -->