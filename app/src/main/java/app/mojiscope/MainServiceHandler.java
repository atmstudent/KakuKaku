package app.mojiscope;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import app.mojiscope.Ocr.OcrResult;
import app.mojiscope.Windows.InformationWindow;
import app.mojiscope.Windows.InstantKanjiWindow;
import app.mojiscope.Windows.WindowCoordinator;

/**
 * Created by 0xbad1d3a5 on 4/15/2016.
 */
public class MainServiceHandler extends Handler {

    private static final String TAG = MainServiceHandler.class.getName();

    private MainService mMojiscopeService;
    private WindowCoordinator mWindowCoordinator;

    public MainServiceHandler(MainService mainService, WindowCoordinator windowCoordinator)
    {
        mMojiscopeService = mainService;
        mWindowCoordinator = windowCoordinator;
    }

    @Override
    public void handleMessage(Message message)
    {
        if (message.obj instanceof String){
            Toast.makeText(mMojiscopeService, message.obj.toString(), Toast.LENGTH_SHORT).show();
        }
        else if (message.obj instanceof OcrResult)
        {
            OcrResult result = (OcrResult) message.obj;

            Log.d(TAG, result.toString());

            if (result.getDisplayData().getInstantMode())
            {
                InstantKanjiWindow instantKanjiWindow = mWindowCoordinator.getWindowOfType(Constants.WINDOW_INSTANT_KANJI);
                instantKanjiWindow.setResult(result.getDisplayData());
                instantKanjiWindow.show();
            }
            else {
                InformationWindow infoWindow = mWindowCoordinator.getWindowOfType(Constants.WINDOW_INFO);
                infoWindow.setResult(result.getDisplayData());
                // The first character is looked up right away, like when the text comes from the share menu
                if (!result.getDisplayData().getSquareChars().isEmpty()) {
                    infoWindow.performSearch(result.getDisplayData().getSquareChars().get(0));
                }
                infoWindow.show();
            }
        }
        else {
            Toast.makeText(mMojiscopeService, String.format("Unable to handle type: %s", message.obj.getClass().getName()), Toast.LENGTH_SHORT).show();
        }
    }
}