package com.example.smiledetection;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;

public class Result extends AppCompatActivity {
    BarChart barChart;
    PieChart pieChart;
    LineChart lineChart;
    int [][] timeExp;
    ArrayList lineEntries;
    LineData lineData;
    LineDataSet lineDataSet;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Bundle extras = getIntent().getExtras();
        timeExp=new int[3600][3600];
        TextView textView = findViewById(R.id.textView3);
        textView.setText(extras.getString("result"));
        int maxemo=0, maxind=-1;

        String[] exps = extras.getString("result").split(",");
        String[] curve=extras.getString("curve").split("\n");
        for(int i=0;i<curve.length;i++){
            timeExp[i][0]=Integer.parseInt(curve[i].split(":")[0]);
            timeExp[i][1]=Integer.parseInt(curve[i].split(":")[1]);
        }
        for(int i=0;i<4;i++){
            if(maxemo<Integer.parseInt(exps[i])) {
                maxemo = Integer.parseInt(exps[i]);
                maxind=i;
            }

        }
        int avg=0;
        for(int j=0;j<=3;j++)
        {
            if(j!=Integer.parseInt(exps[5]) && j!=1){
                avg=avg+Integer.parseInt(exps[j]);
            }
        }
        avg=avg/3;
        int k=Integer.parseInt(exps[5]);
        // attention detection (binary)
        if(Integer.parseInt(exps[5])==maxind) {
            textView.setText("You are engaged");
        }else {
            if (Integer.parseInt(exps[5]) != maxind && maxind == 1 && Integer.parseInt(exps[k]) > 0) {

                if (Integer.parseInt(exps[k]) >= avg) {
                    textView.setText("You are engaged");
                } else {
                    int change = 0;
                    //check for rate of exp change
                    for (int i = 0; i < curve.length - 1; i++) {
                        if (timeExp[i][0] != timeExp[i + 1][0])
                            change++;
                    }
                    if (change >= .4 * curve.length)
                        textView.setText("You are engaged");
                    else
                        textView.setText("You are not engaged");

                }
            } else {
                textView.setText("You are not engaged");

            }
        }
        // attention score (continuous)
        float score=0, den=0, hap=0,neu=0, ang=0, sur=0 ;
        if(k!=1 && k!=4) {
            for (int i = 0; i < 4; i++) {
                if (i != 1) {
                    den = den + Integer.parseInt(exps[i]);
                }
            }
            if (den > 0) {
                score = (Integer.parseInt(exps[k]) / den) * 100;
            } else
                score = 0;
            textView.setText(textView.getText() + "\n Your engagement score is " + score);
        }else{
            if(k==1) {
                for (int i = 0; i < 4; i++) {

                    den = den + Integer.parseInt(exps[i]);

                }
                if (den > 0) {
                    score = (Integer.parseInt(exps[k]) / den) * 100;
                } else
                    score = 0;
                textView.setText(textView.getText() + "\n Your attention score is " + score);
            }
            if(k==4){
                for (int i = 0; i < 4; i++) {

                    den = den + Integer.parseInt(exps[i]);

                }
                if (den > 0) {
                    hap = (Integer.parseInt(exps[0]) / den) * 100;
                    neu = (Integer.parseInt(exps[1]) / den) * 100;
                    ang = (Integer.parseInt(exps[2]) / den) * 100;
                    sur = (Integer.parseInt(exps[3]) / den) * 100;
                } else{
                    hap = 0;
                    neu = 0;
                    ang = 0;
                    sur = 0;}
                textView.setText(textView.getText() + "\n Your attention score is: Happiness  " + hap + ", Neutral " +neu +", Anger "+ang+", Surprise/Fear "+sur);
            }

        }



        barChart=(BarChart) findViewById(R.id.barChart);
        lineChart=(LineChart) findViewById(R.id.linechart);
//        pieChart=(PieChart) findViewById(R.id.pieChart);
        ArrayList<BarEntry> barEntries=new ArrayList<>();
        ArrayList<PieEntry> pieEntries=new ArrayList<>();
        lineEntries = new ArrayList<>();
        final ArrayList<String> xAxisLabel = new ArrayList<>();
        xAxisLabel.add("H");
        xAxisLabel.add("N");
        xAxisLabel.add("A");
        xAxisLabel.add("S");
        // xAxisLabel.add("");
        Float total=Float.parseFloat(exps[0])+Float.parseFloat(exps[1])+Float.parseFloat(exps[2])+Float.parseFloat(exps[3]);
        for(int i=0;i<4;i++) {
            BarEntry barEntry = new BarEntry(i, (Float.parseFloat(exps[i])/total)*100);
            PieEntry pieEntry = new PieEntry(i, (Float.parseFloat(exps[i])/total)*100);
            barEntries.add(barEntry);
            pieEntries.add(pieEntry);
        }
        barChart.getXAxis().setAxisMinimum(0f);     // Better remove setAxisMinValue(), as it deprecated
        //barChart.groupBars(0, groupSpace, barSpace);

        BarDataSet barDataSet=new BarDataSet(barEntries,"Expressions Found");
        barDataSet.setColors(ColorTemplate.COLORFUL_COLORS);
        barDataSet.setDrawValues(false);
        barChart.setData(new BarData(barDataSet));
        barChart.animateY(5000);
        barChart.getDescription().setText("Expression Distribution");
        barChart.getDescription().setTextColor(Color.BLUE);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xAxisLabel));
        barChart.getAxisLeft().setTextColor(Color.GRAY); // left y-axis
        barChart.getAxisRight().setTextColor(Color.GRAY); // right y-axis
        barChart.getXAxis().setTextColor(Color.GRAY);
        barChart.getLegend().setTextColor(Color.GRAY);
        barChart.getDescription().setTextColor(Color.GRAY);
        lineEntries = new ArrayList<>();
        for(int i=0;i<timeExp.length;i++) {
            lineEntries.add(new Entry(timeExp[i][1], timeExp[i][0]));
        }
        lineChart.animateY(5000);
        lineDataSet = new LineDataSet(lineEntries, "Expression Change");
        lineData = new LineData(lineDataSet);
        lineDataSet.setDrawFilled(true);
        lineChart.setData(lineData);
        lineDataSet.setColors(ColorTemplate.JOYFUL_COLORS);
        lineDataSet.setValueTextColor(Color.BLACK);
        lineDataSet.setValueTextSize(18f);
        lineChart.getDescription().setText("Expression Change");
        lineChart.getAxisLeft().setTextColor(Color.GRAY); // left y-axis
        lineChart.getAxisRight().setTextColor(Color.GRAY); // right y-axis
        lineChart.getXAxis().setTextColor(Color.GRAY);
        lineChart.getLegend().setTextColor(Color.GRAY);
        lineChart.getDescription().setTextColor(Color.GRAY);


    }
}
