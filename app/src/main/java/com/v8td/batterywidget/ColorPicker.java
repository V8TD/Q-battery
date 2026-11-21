package com.v8td.batterywidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorDrawable;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Редактор цвета (0.7): превью, HEX, проверка читаемости
 *  и таблица 7 групп × 4 стиля × 24 оттенка (672 цвета) одним полотном. */
final class ColorPicker {

    interface Callback {
        void onColor(int color);
    }

    private static final String[] GROUP_NAMES =
            {"Красный", "Оранжевый", "Жёлтый", "Зелёный", "Бирюзовый", "Синий", "Фиолетовый"};
    private static final int[] GROUP_HUES = {0, 28, 55, 120, 178, 220, 278};
    private static final String[] STYLE_NAMES = {"Стандарт", "Неон", "Пастель", "Карамель"};

    /** Таблица: в каждом стиле 24 оттенка, сверху тёмные, снизу светлые.
     *  STYLE_SAT — насыщенность стиля; V_MIN/V_MAX — яркость верхней/нижней строки.
     *  Эти три строки — единственное «на вкус»: легко подкрутить, если стиль не попал. */
    private static final int ROWS = 24;
    private static final float[] STYLE_SAT = {0.72f, 0.92f, 0.35f, 0.82f};
    private static final float[] STYLE_V_MIN = {0.18f, 0.25f, 0.60f, 0.28f};
    private static final float[] STYLE_V_MAX = {1.00f, 1.00f, 1.00f, 0.78f};

    private static final Pattern HEX_PATTERN = Pattern.compile("#?[0-9A-Fa-f]{6}");

    private ColorPicker() {
    }

    static int contrast(int color) {
        int luma = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
        return luma > 145 ? 0xFF10161F : 0xFFFFFFFF;
    }

    static String toHex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    static Integer parseHex(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                b.append(s.charAt(i)).append(s.charAt(i));
            }
            s = b.toString();
        }
        if (s.length() != 6) return null;
        try {
            return (int) (0xFF000000L | Long.parseLong(s, 16));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ищет HEX в произвольном тексте: сначала целиком, потом первое совпадение. */
    private static Integer extractHex(String raw) {
        if (raw == null) return null;
        Integer whole = parseHex(raw);
        if (whole != null) return whole;
        Matcher m = HEX_PATTERN.matcher(raw);
        return m.find() ? parseHex(m.group()) : null;
    }

    /** Цвет клетки таблицы: группа, столбец-стиль, строка (0 = самая тёмная). */
    private static int tableColor(int group, int style, int row) {
        float v = STYLE_V_MIN[style]
                + (STYLE_V_MAX[style] - STYLE_V_MIN[style]) * row / (ROWS - 1);
        return Color.HSVToColor(new float[]{GROUP_HUES[group], STYLE_SAT[style], v});
    }

    private static long dist2(int a, int b) {
        int dr = Color.red(a) - Color.red(b);
        int dg = Color.green(a) - Color.green(b);
        int db = Color.blue(a) - Color.blue(b);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }

    /** Ближайшая клетка таблицы: {группа, стиль, строка, 1 = точное совпадение}. */
    private static int[] nearest(int color) {
        int bg = 0, bs = 0, br = 0;
        long best = Long.MAX_VALUE;
        for (int g = 0; g < GROUP_HUES.length; g++) {
            for (int s = 0; s < STYLE_NAMES.length; s++) {
                for (int r = 0; r < ROWS; r++) {
                    long d = dist2(color, tableColor(g, s, r));
                    if (d < best) {
                        best = d;
                        bg = g;
                        bs = s;
                        br = r;
                    }
                }
            }
        }
        return new int[]{bg, bs, br, best == 0 ? 1 : 0};
    }

    /** «Фото» для проверки читаемости: пёстрый фон рисуем кодом,
     *  чтобы не класть в репозиторий бинарную картинку. */
    private static Bitmap makePhoto() {
        int w = 240;
        int h = 160;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Random rnd = new Random(42);
        cv.drawColor(Color.HSVToColor(new float[]{205f, 0.30f, 0.32f}));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 48; i++) {
            p.setColor(Color.HSVToColor(new float[]{
                    rnd.nextFloat() * 360f,
                    0.30f + rnd.nextFloat() * 0.55f,
                    0.25f + rnd.nextFloat() * 0.70f}));
            p.setAlpha(80 + rnd.nextInt(130));
            cv.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h,
                    8f + rnd.nextFloat() * 38f, p);
        }
        return bmp;
    }

    static void open(Activity a, String title, int initial, Callback callback) {
        final int[] cur = {initial};
        final int[] near0 = nearest(initial);
        final int[] group = {near0[0]};
        final boolean[] lockHex = {false};

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpPx(a, 20), dpPx(a, 10), dpPx(a, 20), dpPx(a, 12));

        ScrollView scroll = new ScrollView(a);

        // --- 1. Большое превью. Тап — прокрутка к таблице. ---
        TextView preview = new TextView(a);
        preview.setText(title);
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(22);
        preview.setOnClickListener(v ->
                scroll.smoothScrollTo(0, Math.max(0, tableTop(box) - dpPx(a, 8))));

        // --- 2. HEX: ввод, копирование, вставка. ---
        EditText hex = new EditText(a);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hex.setTextSize(15);
        hex.setTextColor(a.getColor(R.color.text));
        hex.setHintTextColor(a.getColor(R.color.muted));
        hex.setHint("#35D9FF");
        hex.setText(toHex(initial));

        Button copy = smallButton(a, "Копировать");
        Button paste = smallButton(a, "Вставить");
        TextView hexStatus = new TextView(a);
        hexStatus.setTextSize(12);
        hexStatus.setTextColor(a.getColor(R.color.muted));
        hexStatus.setPadding(dpPx(a, 2), dpPx(a, 4), dpPx(a, 2), 0);

        LinearLayout hexRow = new LinearLayout(a);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);

        // --- 3. Читаемость цифр на четырёх фонах. ---
        final TextView[] tiles = new TextView[4];
        String[] captions = {"Тёмный", "Светлый", "Цветной", "Фото"};
        int[] tileColors = {0xFF0E131C, 0xFFF3F5F8, 0xFF3A6EA5, 0};
        Bitmap photo = makePhoto();
        LinearLayout tileGrid = new LinearLayout(a);
        tileGrid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                int idx = r * 2 + c;
                LinearLayout cell = new LinearLayout(a);
                cell.setOrientation(LinearLayout.VERTICAL);
                TextView tile = new TextView(a);
                tile.setText("88");
                tile.setGravity(Gravity.CENTER);
                tile.setTextSize(24);
                tile.setTypeface(Typeface.DEFAULT_BOLD);
                if (idx == 3) {
                    tile.setBackground(new BitmapDrawable(a.getResources(), photo));
                } else {
                    tile.setBackground(new ColorDrawable(tileColors[idx]));
                }
                tile.setOutlineProvider(rounded(a, dpPx(a, 10)));
                tile.setClipToOutline(true);
                tiles[idx] = tile;
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, dpPx(a, 62));
                tlp.setMargins(dpPx(a, 3), 0, dpPx(a, 3), 0);
                cell.addView(tile, tlp);
                TextView cap = new TextView(a);
                cap.setText(captions[idx]);
                cap.setTextSize(11);
                cap.setTextColor(a.getColor(R.color.muted));
                cap.setGravity(Gravity.CENTER);
                cap.setPadding(0, dpPx(a, 3), 0, 0);
                cell.addView(cap, new LinearLayout.LayoutParams(-1, -2));
                row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = dpPx(a, 4);
            tileGrid.addView(row, rlp);
        }

        // --- 4. Группы — кнопки с названиями цветов. ---
        HorizontalScrollView groupScroll = new HorizontalScrollView(a);
        groupScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout groupRow = new LinearLayout(a);
        groupRow.setOrientation(LinearLayout.HORIZONTAL);
        groupScroll.addView(groupRow);
        Button[] groupBtns = new Button[GROUP_NAMES.length];
        for (int i = 0; i < GROUP_NAMES.length; i++) {
            Button b = new Button(a);
            b.setText(GROUP_NAMES[i]);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(dpPx(a, 12), dpPx(a, 8), dpPx(a, 12), dpPx(a, 8));
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
            glp.setMargins(dpPx(a, 2), dpPx(a, 2), dpPx(a, 2), dpPx(a, 2));
            groupRow.addView(b, glp);
            groupBtns[i] = b;
        }

        // --- 5. Заголовки стилей и сама таблица одним полотном. ---
        LinearLayout headerRow = new LinearLayout(a);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int s = 0; s < STYLE_NAMES.length; s++) {
            TextView h = new TextView(a);
            h.setText(STYLE_NAMES[s]);
            h.setTextSize(11);
            h.setTextColor(a.getColor(R.color.muted));
            h.setGravity(Gravity.CENTER);
            headerRow.addView(h, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        ShadeTable table = new ShadeTable(a);
        table.setGroup(group[0]);

        LinearLayout tableBlock = new LinearLayout(a);
        tableBlock.setOrientation(LinearLayout.VERTICAL);
        tableBlock.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dpPx(a, 4);
        tableBlock.addView(table, blp);

        // --- Перерисовка кнопок групп. ---
        final Runnable[] drawGroups = new Runnable[1];
        drawGroups[0] = () -> {
            for (int i = 0; i < groupBtns.length; i++) {
                boolean sel = i == group[0];
                Button b = groupBtns[i];
                b.setTextColor(a.getColor(sel ? R.color.text : R.color.muted));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(a.getColor(R.color.button));
                bg.setCornerRadius(dpPx(a, 8));
                bg.setStroke(dpPx(a, sel ? 2 : 1),
                        sel ? a.getColor(R.color.accent) : a.getColor(R.color.stroke));
                b.setBackground(bg);
            }
        };

        // --- Главное обновление: превью, HEX, статус, кольцо в таблице, плитки. ---
        final Runnable apply = () -> {
            int color = cur[0];
            GradientDrawable pbg = new GradientDrawable();
            pbg.setColor(color);
            pbg.setCornerRadius(dpPx(a, 12));
            preview.setBackground(pbg);
            preview.setTextColor(contrast(color));
            if (!lockHex[0]) {
                lockHex[0] = true;
                hex.setText(toHex(color));
                hex.setSelection(hex.getText().length());
                lockHex[0] = false;
            }
            int[] near = nearest(color);
            hexStatus.setText(near[3] == 1
                    ? "Этот цвет есть в таблице — столбец «" + STYLE_NAMES[near[1]] + "»"
                    : "Свой цвет — ближайший подсвечен в таблице");
            if (group[0] != near[0]) {
                group[0] = near[0];
                table.setGroup(near[0]);
                drawGroups[0].run();
            }
            table.setMark(near[1], near[2], near[3] == 1);
            for (TextView t : tiles) t.setTextColor(color);
        };

        // --- Привязка событий. ---
        for (int i = 0; i < groupBtns.length; i++) {
            final int gi = i;
            groupBtns[i].setOnClickListener(v -> {
                group[0] = gi;
                table.setGroup(gi);
                drawGroups[0].run();
            });
        }

        table.setOnCellTap(color -> {
            cur[0] = color;
            apply.run();
        });

        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (lockHex[0]) return;
                Integer parsed = parseHex(s.toString());
                if (parsed == null) return;
                cur[0] = parsed;
                apply.run();
            }
        });

        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("color", hex.getText().toString()));
                Toast.makeText(a, "Скопировано", Toast.LENGTH_SHORT).show();
            }
        });

        paste.setOnClickListener(v -> {
            CharSequence s = null;
            try {
                ClipboardManager cm =
                        (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    s = cm.getPrimaryClip().getItemAt(0).coerceToText(a);
                }
            } catch (Throwable ignored) {
            }
            Integer parsed = extractHex(s == null ? null : s.toString());
            if (parsed == null) {
                Toast.makeText(a, "В буфере не найден HEX-цвет", Toast.LENGTH_SHORT).show();
            } else {
                cur[0] = parsed;
                apply.run();
            }
        });

        // --- Сборка страницы. ---
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dpPx(a, 64));
        previewLp.topMargin = dpPx(a, 4);
        box.addView(preview, previewLp);
        box.addView(hint(a, "Нажми на превью — прокрутка к таблице цветов."));
        box.addView(label(a, "Код цвета (HEX)"));
        hexRow.addView(hex, new LinearLayout.LayoutParams(0, -2, 1f));
        hexRow.addView(copy, new LinearLayout.LayoutParams(-2, -2));
        hexRow.addView(paste, new LinearLayout.LayoutParams(-2, -2));
        box.addView(hexRow, new LinearLayout.LayoutParams(-1, -2));
        box.addView(hexStatus, new LinearLayout.LayoutParams(-1, -2));
        box.addView(label(a, "Читаемость цифр на фоне"));
        box.addView(tileGrid, new LinearLayout.LayoutParams(-1, -2));
        box.addView(label(a, "Цветовая группа"));
        box.addView(groupScroll, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(-1, -2);
        tblp.topMargin = dpPx(a, 6);
        box.addView(tableBlock, tblp);
        box.addView(hint(a, "Нажми клетку — выбрать цвет. Кольцом отмечен выбранный"
                + " (или ближайший к своему) цвет."));

        drawGroups[0].run();
        apply.run();

        scroll.addView(box);

        new AlertDialog.Builder(a)
                .setTitle("Цвет диапазона")
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d, w) -> callback.onColor(cur[0]))
                .show();

        // Автопрокрутка к строке выбранного (или ближайшего) цвета.
        scroll.post(() -> {
            int y = tableBlock.getTop() + headerRow.getHeight()
                    + near0[2] * table.rowHeight() - scroll.getHeight() / 3;
            scroll.scrollTo(0, Math.max(0, y));
        });
    }

    /** Позиция блока таблицы внутри страницы (для прокрутки). */
    private static int tableTop(LinearLayout box) {
        for (int i = 0; i < box.getChildCount(); i++) {
            View v = box.getChildAt(i);
            if (v instanceof LinearLayout
                    && ((LinearLayout) v).getOrientation() == LinearLayout.VERTICAL
                    && v.getChildAt(0) instanceof TextView
                    && v.getChildAt(1) instanceof ShadeTable) {
                return v.getTop();
            }
        }
        return 0;
    }

    private static Button smallButton(Activity a, String text) {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dpPx(a, 10), 0, dpPx(a, 10), 0);
        b.setTextColor(a.getColor(R.color.text));
        return b;
    }

    private static TextView label(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(a.getColor(R.color.muted));
        t.setPadding(0, dpPx(a, 14), 0, dpPx(a, 4));
        return t;
    }

    private static TextView hint(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(a.getColor(R.color.muted));
        t.setPadding(dpPx(a, 2), dpPx(a, 4), dpPx(a, 2), 0);
        return t;
    }

    private static ViewOutlineProvider rounded(Activity a, float radiusPx) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        };
    }

    private static int dpPx(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    /** Вся таблица (4 столбца × 24 строки) рисуется одним View, а не сотней клеток. */
    private static final class ShadeTable extends View {

        interface OnCellTap {
            void onTap(int color);
        }

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int cellH;
        private int group = 0;
        private int markCol = -1;
        private int markRow = -1;
        private boolean markExact = false;
        private OnCellTap listener;
        private float lastX = -1;
        private float lastY = -1;

        ShadeTable(Context c) {
            super(c);
            cellH = dpPx(c, 32);
            ring.setStyle(Paint.Style.STROKE);
        }

        int rowHeight() {
            return cellH;
        }

        void setGroup(int g) {
            if (group != g) {
                group = g;
                invalidate();
            }
        }

        void setMark(int col, int row, boolean exact) {
            markCol = col;
            markRow = row;
            markExact = exact;
            invalidate();
        }

        void setOnCellTap(OnCellTap l) {
            listener = l;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), ROWS * cellH);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Context c = getContext();
            float gap = dpPx(c, 2);
            float radius = dpPx(c, 5);
            float cw = getWidth() / 4f;
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < 4; col++) {
                    fill.setColor(tableColor(group, col, row));
                    rect.set(col * cw + gap, row * cellH + gap,
                            (col + 1) * cw - gap, (row + 1) * cellH - gap);
                    cv.drawRoundRect(rect, radius, radius, fill);
                }
            }
            if (markCol >= 0 && markRow >= 0) {
                ring.setColor(c.getColor(R.color.accent));
                float sw = dpPx(c, markExact ? 3 : 2);
                ring.setStrokeWidth(sw);
                rect.set(markCol * cw + gap, markRow * cellH + gap,
                        (markCol + 1) * cw - gap, (markRow + 1) * cellH - gap);
                rect.inset(sw / 2f, sw / 2f);
                cv.drawRoundRect(rect, radius, radius, ring);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            lastX = e.getX();
            lastY = e.getY();
            if (e.getAction() == MotionEvent.ACTION_UP) {
                performClick();
            }
            return true;
        }

        @Override
        public boolean performClick() {
            if (listener != null && last
