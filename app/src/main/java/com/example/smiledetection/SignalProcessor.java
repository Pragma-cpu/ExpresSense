package com.example.smiledetection;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.StringReader;
import java.util.Arrays;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.psambit9791.jdsp.signal.CrossCorrelation;
import com.github.psambit9791.jdsp.transform.DiscreteFourier;
import com.github.psambit9791.jdsp.transform.Hilbert;

import org.apache.commons.math3.complex.Complex;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SignalProcessor extends AppCompatActivity {

    private int SAMPLE_LENGTH = 2048;
    private int NUMBER_OF_INITIAL = 5;
    private int NUMBER_OF_SAMPLE_POINTS = 15;
    private double[][] mixedSamplesPhase;
    private double[][] mixedSamplesAmp;
    private double[][] initialPoints;
    private double[] distances;
    private double[] center;
    private int countLow;
    private int displayTime = 0;
    int FREQ_BIN = 0;
    String topFive="";
    int BINS_TOP=5;
    int[] FIVE_FREQ_BIN = new int[BINS_TOP];
    private double thresBlink;
    private double thresSmile;
    private double thresTimeLow;
    private int iterator = 0;
    private int chirp_cnt = 0;
    private double distPre = 0;
    private double distCur = 0;
    private double var;
    private boolean BinInitialized= false;
    private boolean ThresInitialized = false;
    private File file;
    private Context context;
    String exp;
    String url="https://expressense.herokuapp.com/predict";
    //Write to file
    String filename="SleepSmile.txt";


    public SignalProcessor(Context _context){
        mixedSamplesPhase = new double[NUMBER_OF_INITIAL][SAMPLE_LENGTH];
        mixedSamplesAmp = new double[NUMBER_OF_INITIAL][SAMPLE_LENGTH];
        initialPoints = new double[NUMBER_OF_SAMPLE_POINTS][2];
        distances = new double[NUMBER_OF_SAMPLE_POINTS];
        center = new double[]{0.0, 0.0};
        context = _context;
    }

    void initializeFreq_Bin(){
        //FREQ_BIN = binIndex();
        FREQ_BIN=binIndex();
        BinInitialized = true;

//        for(int h=0;h<FIVE_FREQ_BIN.length;h++)
//            topFive=topFive+","+FIVE_FREQ_BIN[h];
        getFileTxt("Frequency Bins "+FREQ_BIN + "\n", "SmileSleep.txt");
        //Log.d("initialization",String.format("FreqBin: %d", topFive));
        //getFileTxt(FREQ_BIN+"\n", "SmileSleep.txt");
    }

    void initializeThresholds(){
        ThresInitialized = true;
        var = variance();
        thresBlink = 3*var;
        thresSmile = 10*var;
        thresTimeLow = 2;
        Log.d("initialization",String.format("variance: %f", var));
    }

    void FourierTransform(double[] chirp, double[] direct, double[] record) {
        double[] rSample = signalMultiplication(chirp, record);
        double[] dSample = signalMultiplication(chirp, direct);

        Hilbert h = new Hilbert(rSample);
        h.hilbertTransform();
        double[][] analyticRSample = h.getOutput();

        Hilbert d = new Hilbert(dSample);
        d.hilbertTransform();
        double[][] analyticDSample = d.getOutput();

        Hilbert f = new Hilbert(direct);
        f.hilbertTransform();
        double[][] analyticDirect = f.getOutput();

        Hilbert g = new Hilbert(record);
        g.hilbertTransform();
        double[][] analyticRecord = g.getOutput();

        double[] mixedRSignal = new double[SAMPLE_LENGTH];
        double[] mixedDSignal = new double[SAMPLE_LENGTH];

        for (int i = 0; i < SAMPLE_LENGTH; i++) {
            mixedRSignal[i] = analyticRecord[i][0] * analyticRSample[i][0] + analyticRecord[i][1] * analyticRSample[i][1];
            mixedDSignal[i] = analyticDirect[i][0] * analyticDSample[i][0] + analyticDirect[i][1] * analyticDSample[i][1];
        }

        DiscreteFourier fft = new DiscreteFourier(mixedRSignal);
        fft.dft();
        Complex[] mixedRFftComplex = fft.returnComplex(true);

        DiscreteFourier fftD = new DiscreteFourier(mixedDSignal);
        fftD.dft();
        Complex[] mixedDFftcomplex = fftD.returnComplex(true);


        if (iterator < NUMBER_OF_INITIAL && !BinInitialized) {
            for (int G = 0; G < SAMPLE_LENGTH / 2; G++) {
                mixedSamplesPhase[iterator][G] = mixedRFftComplex[G].getArgument() - mixedDFftcomplex[G].getArgument();
                mixedSamplesAmp[iterator][G] = mixedRFftComplex[G].abs() - mixedDFftcomplex[G].abs();
            }
        }
        long time = System.currentTimeMillis();
        chirp_cnt++;

//        String Amplitude = "";
//        String Angle = "";
        Complex value = mixedRFftComplex[FREQ_BIN].subtract(mixedDFftcomplex[FREQ_BIN]);
        String Amplitude = value.abs() + " ";
        String Angle = value.getArgument() + " ";

//        for(int i=0; i<mixedRFftComplex.length; i++){
//
//            Complex value = mixedRFftComplex[i].subtract(mixedDFftcomplex[i]);
//            Amplitude += value.abs() + " ";
//            Angle += value.getArgument() + " ";
//        }
//        if(BinInitialized) {
//            initialPoints[iterator][0] = amplitude * Math.cos(angle);
//            initialPoints[iterator][1] = amplitude * Math.sin(angle);
//
//            distances[iterator] = pointDistanceCartesian(initialPoints[iterator], center);
//        }
        iterator = (iterator + 1) % NUMBER_OF_SAMPLE_POINTS;
//        if(iterator == NUMBER_OF_INITIAL){
//            double[] v = prattNewton(initialPoints);
//            center[0] = v[0];
//            center[1] = v[1];
//        }

        getFileTxt(chirp_cnt + "," + time + ",:" + Amplitude + "," + Angle + "\n", "SmileSleep.txt");
        if (chirp_cnt % 2 == 0) {
            StringRequest STRINGREQ = new StringRequest(Request.Method.POST, url,  new com.android.volley.Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    //Log.d("expression=", "here we are");
                    try {

                        JSONObject jsonObject = new JSONObject(response);
                        exp = jsonObject.getString("expression:");
                        Log.d("expression=", exp + "");

                    } catch (JSONException e) {
                        Log.d("expression=", " json here");
                        e.printStackTrace();
                    }

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    error.printStackTrace();
                    Log.d("error", error + ":(");

                }
            }) {
                @Override
                public Map<String,String> getParams() {
                    Map<String, String> params = new HashMap<String, String>();
                    Log.d("Print time", String.format("%d", time));
                    Log.d("initialization", chirp_cnt + "," + time + "\n Amplitude:" + Amplitude + "\n Angle:" + Angle + "\n");
                    try {
                        params.put("amp", Amplitude + "");
                        params.put("phase", Angle + "");
//                        params.put("name",Amplitude+"");
//                        params.put("job",Angle + "");
                        //params.put("profile_score",Angle + "");


                    }catch(Exception e) { }
                    return params;
                }

            };
            STRINGREQ.setRetryPolicy(new DefaultRetryPolicy(
                    20000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            RequestQueue QUEUE = Volley.newRequestQueue(context);
            QUEUE.add(STRINGREQ);

        }

    }

    double[] signalMultiplication(double[] x, double[] y){
        CrossCorrelation cc = new CrossCorrelation(x, y);
        double[] out = cc.crossCorrelate("valid");

        int maxAt = 0;
        for(int i=0; i<out.length; i++){
            maxAt = out[i] > out[maxAt] ? i : maxAt;
        }

        double[] sample = new double[x.length - maxAt];
        for(int i=maxAt; i<x.length; i++){
            sample[i-maxAt] = x[i];
        }
        return sample;
    }

    int binIndex()
    {
        int maxFiveInd[]=new int[ BINS_TOP];
        int bin = 1000;
        double maxRange[] = new double[SAMPLE_LENGTH/2];
        double maxRangeDup[] = new double[SAMPLE_LENGTH/2];
        double maxRange1=0;
        double maxFiveNum[]=new double[ BINS_TOP];
        for(int h=0;h<BINS_TOP;h++){
            maxFiveInd[h]=0;
            maxFiveNum[h]=0;
        }

        for(int j = 0; j<SAMPLE_LENGTH/2; j++)
        {
            double minPhase = 4.00, maxPhase = -4.00;
            double minAmp = -100.00, maxAmp = 100.00;

            for(int i=0; i<NUMBER_OF_INITIAL; i++)
            {
                minPhase = Math.min(minPhase, mixedSamplesPhase[i][j]);
                maxPhase = Math.max(maxPhase, mixedSamplesPhase[i][j]);

                minAmp = Math.min(minAmp, mixedSamplesAmp[i][j]);
                maxAmp = Math.max(maxAmp, mixedSamplesAmp[i][j]);

                //Log.d("initialization",minPhase+"\n"+maxPhase);
            }
            //if(maxRange[j] < (maxPhase - minPhase))
            //{
                //maxRange[j] = maxPhase - minPhase;

            //}
            double currRange = 0.7*(maxPhase - minPhase) + 0.3*(maxAmp - minAmp);
            if(maxRange1 < currRange){
                maxRange1 = currRange;
                bin=j;
            }
        }

//        for(int y=0;y<maxRange.length;y++)
//        {
//            double v=maxRange[y];
//            for(int z=0;z<maxFiveInd.length;z++)
//            {
//                if(maxFiveNum[z]<v){
//                    maxFiveNum[z]=v;
//                    maxFiveInd[z]=y;
//                    break;
//                }
//            }
//        }
        return bin;
    }

    double pointDistancePolar(double r1, double theta1, double r2, double theta2)
    {
        return Math.sqrt(r1*r1 + r2*r2 - 2*r1*r2*Math.cos((theta1-theta2)*Math.PI));
    }

    double pointDistanceCartesian(double[] p1, double[] p2)
    {
        return Math.sqrt((p2[0]-p1[0])*(p2[0]-p1[0]) + (p2[1]-p1[1])*(p2[1]-p1[1]));
    }

    double variance()
    {
        // Compute mean (average of elements)
        double sum = 0;
        int n = NUMBER_OF_INITIAL;
        for (int i = NUMBER_OF_INITIAL; i < 2*NUMBER_OF_INITIAL; i++)
            sum += distances[i];

        double mean = (double)sum / (double)n;

        // Compute sum squared differences with mean.
        double sqDiff = 0;
        for (int i = 0; i < n; i++)
            sqDiff += (distances[i] - mean) * (distances[i] - mean);

        return (double)sqDiff / n;
    }

    int checkStatus()		//thresBlink, countLow, thresLow, thresSmile
    {
        int status = 2;

        if(distPre - distCur > thresBlink && countLow==0){
            countLow++;
        }
        else if((countLow != 0) && (Math.abs(distCur - distPre) < 5*var)){
            countLow++;
        }

        if(countLow >= thresTimeLow)
        {
            status = 0;
            Log.e("Status", "Sleeping");
            countLow = 0;
            displayTime = 0;
            return status;
        }

        if(distCur - distPre > thresSmile)
        {
            status = 1;
            displayTime = 0;
            Log.e("Status","Smiling");
            return status;
        }

        displayTime++;

        if(displayTime > 3){
            status = -1;
        }

        return status;
    }


    /**
     * Pratt method (Newton style)
     *
     *  //double[n][2]
     *            containing n (<i>x</i>, <i>y</i>) coordinates
     * @return double[] containing (<i>x</i>, <i>y</i>) centre and radius
     *
     */
    public static double[] prattNewton(final double[][] points) {
        final int nPoints = points.length;
        if (nPoints < 3)
            throw new IllegalArgumentException("Too few points");
        final double[] centroid = getCentroid2D(points);
        double Mxx = 0, Myy = 0, Mxy = 0, Mxz = 0, Myz = 0, Mzz = 0;

        for (int i = 0; i < nPoints; i++) {
            final double Xi = points[i][0] - centroid[0];
            final double Yi = points[i][1] - centroid[1];
            final double Zi = Xi * Xi + Yi * Yi;
            Mxy += Xi * Yi;
            Mxx += Xi * Xi;
            Myy += Yi * Yi;
            Mxz += Xi * Zi;
            Myz += Yi * Zi;
            Mzz += Zi * Zi;
        }
        Mxx /= nPoints;
        Myy /= nPoints;
        Mxy /= nPoints;
        Mxz /= nPoints;
        Myz /= nPoints;
        Mzz /= nPoints;

        final double Mz = Mxx + Myy;
        final double Cov_xy = Mxx * Myy - Mxy * Mxy;
        final double Mxz2 = Mxz * Mxz;
        final double Myz2 = Myz * Myz;

        final double A2 = 4 * Cov_xy - 3 * Mz * Mz - Mzz;
        final double A1 = Mzz * Mz + 4 * Cov_xy * Mz - Mxz2 - Myz2 - Mz * Mz * Mz;
        final double A0 = Mxz2 * Myy + Myz2 * Mxx - Mzz * Cov_xy - 2 * Mxz * Myz * Mxy + Mz * Mz * Cov_xy;
        final double A22 = A2 + A2;

        final double epsilon = 1e-12;
        double ynew = 1e+20;
        final int IterMax = 20;
        double xnew = 0;
        for (int iter = 0; iter <= IterMax; iter++) {
            final double yold = ynew;
            ynew = A0 + xnew * (A1 + xnew * (A2 + 4 * xnew * xnew));
            if (Math.abs(ynew) > Math.abs(yold)) {
                System.out.println("Newton-Pratt goes wrong direction: |ynew| > |yold|");
                xnew = 0;
                break;
            }
            final double Dy = A1 + xnew * (A22 + 16 * xnew * xnew);
            final double xold = xnew;
            xnew = xold - ynew / Dy;
            if (Math.abs((xnew - xold) / xnew) < epsilon) {
                break;
            }
            if (iter >= IterMax) {
                System.out.println("Newton-Pratt will not converge");
                xnew = 0;
            }
            if (xnew < 0) {
                System.out.println("Newton-Pratt negative root:  x= " + xnew);
                xnew = 0;
            }
        }
        final double det = xnew * xnew - xnew * Mz + Cov_xy;
        final double x = (Mxz * (Myy - xnew) - Myz * Mxy) / (det * 2);
        final double y = (Myz * (Mxx - xnew) - Mxz * Mxy) / (det * 2);
        final double r = Math.sqrt(x * x + y * y + Mz + 2 * xnew);

        final double[] centreRadius = { x + centroid[0], y + centroid[1], r };
        return centreRadius;
    }

    /**
     * Find the centroid of a set of points in double[n][2] format
     *
     * @param points
     * @return
     */
    private static double[] getCentroid2D(final double[][] points) {
        final double[] centroid = new double[2];
        double sumX = 0;
        double sumY = 0;
        final int nPoints = points.length;

        for (int n = 0; n < nPoints; n++) {
            sumX += points[n][0];
            sumY += points[n][1];
        }

        centroid[0] = sumX / nPoints;
        centroid[1] = sumY / nPoints;

        return centroid;
    }
    public void getFileTxt(String content,String filename)
    {
        try {
            //Log.e("path123","welcome");
            File path = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            file = new File(path, filename);
            FileWriter fw=null;
            BufferedWriter bw = null;
            Log.d("path",file.getAbsolutePath()+file.exists());
            if (!file.exists()) {
                file.createNewFile();
            }
            fw = new FileWriter(file.getAbsolutePath(), true);
            bw= new BufferedWriter(fw);
            bw.write(content);
            bw.close();
        }catch(Exception e) {
            Log.e("Write Errro", e.getMessage());

        }
    }

}
