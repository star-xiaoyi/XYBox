package com.fongmi.android.tv.player.danmaku;
import com.github.catvod.utils.Logger;

import com.fongmi.android.tv.bean.DanmakuData;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.android.AndroidFileSource;
import master.flame.danmaku.danmaku.util.DanmakuUtils;

public class Parser extends BaseDanmakuParser {

    private static final Pattern XML = Pattern.compile("p=\"([^\"]+)\"[^>]*>([^<]+)<");
    private static final Pattern TXT = Pattern.compile("\\[(.*?)\\](.*)");

    @Override
    public Danmakus parse() {
        String line;
        Pattern pattern = null;
        if (mDataSource == null) return null;
        List<DanmakuData> items = new ArrayList<>();
        AndroidFileSource source = (AndroidFileSource) mDataSource;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(source.data()))) {
            while ((line = br.readLine()) != null) {
                if (pattern == null) pattern = line.startsWith("<") ? XML : TXT;
                Matcher matcher = pattern.matcher(line);
                while (matcher.find() && matcher.groupCount() == 2) {
                    try {
                        items.add(new DanmakuData(matcher, mDispDensity));
                    } catch (Exception e) {
                        Logger.e("Error", e);
                    }
                }
            }
            Danmakus result = new Danmakus(IDanmakus.ST_BY_TIME);
            for (int i = 0; i < items.size(); i++) {
                BaseDanmaku item = mContext.mDanmakuFactory.createDanmaku(items.get(i).getType(), mContext);
                if (item.getType() == BaseDanmaku.TYPE_SPECIAL) continue;
                DanmakuUtils.fillText(item, items.get(i).getText());
                item.textShadowColor = items.get(i).getShadow();
                item.textColor = items.get(i).getColor();
                item.flags = mContext.mGlobalFlagValues;
                item.textSize = items.get(i).getSize();
                item.setTime(items.get(i).getTime());
                item.setTimer(mTimer);
                item.index = i;
                synchronized (result.obtainSynchronizer()) {
                    result.addItem(item);
                }
            }
            return result;
        } catch (Exception e) {
            Logger.e("Error", e);
            return null;
        }
    }
}
