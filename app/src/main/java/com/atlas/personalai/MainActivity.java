package com.atlas.personalai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int MIC=10, ACCESS=12;
    private TextView status,heard,reply,voiceInfo,accessInfo;
    private EditText core;
    private Button conversationButton;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final String conversationId=UUID.randomUUID().toString();
    private final List<Voice> voiceChoices=new ArrayList<>();
    private int voiceIndex=-1;
    private boolean conversationMode,resumeAfterSpeech,destroyed;
    private JSONObject pendingAction;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        conversationMode=AtlasStore.conversationMode(this);
        buildUi();
        tts=new TextToSpeech(this,this);
        try{startForegroundService(new Intent(this,AtlasForegroundService.class));}catch(Exception ignored){}
        if(getIntent().getBooleanExtra("atlas_auto_listen",false)) handler.postDelayed(this::listen,900);
        refreshAccess();
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent); setIntent(intent);
        if(intent.getBooleanExtra("atlas_auto_listen",false)) handler.postDelayed(this::listen,350);
    }

    private TextView tv(String s,int sp){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setPadding(20,12,20,12);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);return b;}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);
        ImageView icon=new ImageView(this);icon.setImageResource(com.atlas.personalai.R.drawable.atlas_icon);icon.setAdjustViewBounds(true);
        root.addView(icon,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,220));
        TextView title=tv("ATLAS v5",30);title.setTypeface(Typeface.DEFAULT_BOLD);title.setGravity(Gravity.CENTER_HORIZONTAL);root.addView(title);
        status=tv("STARTING...",16);root.addView(status);
        accessInfo=tv("PHONE ACCESS: checking...",13);root.addView(accessInfo);
        Button grant=button("GRANT PHONE ACCESS");grant.setOnClickListener(v->requestPhoneAccess());root.addView(grant);
        conversationButton=button("");updateConversationButton();conversationButton.setOnClickListener(v->{conversationMode=!conversationMode;AtlasStore.setConversationMode(this,conversationMode);updateConversationButton();toast(conversationMode?"Conversation mode on":"Conversation mode off");});root.addView(conversationButton);
        Button talk=button("TALK TO ATLAS");talk.setOnClickListener(v->listen());root.addView(talk);
        Button interrupt=button("INTERRUPT / LISTEN NOW");interrupt.setOnClickListener(v->{stopSpeech();listen();});root.addView(interrupt);
        voiceInfo=tv("VOICE: loading...",13);root.addView(voiceInfo);
        LinearLayout vr=new LinearLayout(this);vr.setOrientation(LinearLayout.HORIZONTAL);
        Button next=button("NEXT VOICE");next.setOnClickListener(v->nextVoice());vr.addView(next,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        Button slower=button("SLOWER");slower.setOnClickListener(v->adjustRate(-0.05f));vr.addView(slower,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        Button faster=button("FASTER");faster.setOnClickListener(v->adjustRate(0.05f));vr.addView(faster,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        root.addView(vr);
        core=new EditText(this);core.setHint("Atlas Core URL (optional)");core.setSingleLine(true);core.setText(AtlasStore.coreUrl(this));root.addView(core);
        Button save=button("SAVE CORE URL");save.setOnClickListener(v->{AtlasStore.setCoreUrl(this,core.getText().toString());toast("Core URL saved");});root.addView(save);
        Button tools=button("WHAT CAN ATLAS ACCESS?");tools.setOnClickListener(v->speakReply(CapabilityManager.summary(this),false));root.addView(tools);
        heard=tv("You: —",16);root.addView(heard);
        reply=tv("Atlas: —",17);reply.setTypeface(Typeface.DEFAULT_BOLD);root.addView(reply);
        TextView hint=tv("Try: open Spotify • call John • navigate to Reading Hospital • where am I • pause music • next song • what's on my calendar • what did I miss • what's my battery",14);root.addView(hint);
        ScrollView sc=new ScrollView(this);sc.addView(root);setContentView(sc);
    }

    private void requestPhoneAccess(){
        ArrayList<String> p=new ArrayList<>();
        addIfMissing(p,Manifest.permission.RECORD_AUDIO); addIfMissing(p,Manifest.permission.READ_CONTACTS); addIfMissing(p,Manifest.permission.CALL_PHONE);
        addIfMissing(p,Manifest.permission.READ_CALENDAR); addIfMissing(p,Manifest.permission.WRITE_CALENDAR);
        addIfMissing(p,Manifest.permission.ACCESS_COARSE_LOCATION); addIfMissing(p,Manifest.permission.ACCESS_FINE_LOCATION);
        if(android.os.Build.VERSION.SDK_INT>=33) addIfMissing(p,Manifest.permission.POST_NOTIFICATIONS);
        if(p.isEmpty()){toast("Phone access already granted");refreshAccess();return;}
        requestPermissions(p.toArray(new String[0]),ACCESS);
    }
    private void addIfMissing(List<String> list,String perm){if(checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED)list.add(perm);}
    private void refreshAccess(){if(accessInfo!=null)accessInfo.setText("PHONE ACCESS: "+CapabilityManager.summary(this));}
    private void updateConversationButton(){if(conversationButton!=null)conversationButton.setText("CONVERSATION MODE: "+(conversationMode?"ON":"OFF"));}

    private void listen(){
        resumeAfterSpeech=false;stopSpeech();
        if(!SpeechRecognizer.isRecognitionAvailable(this)){speakReply("Speech recognition isn't available on this phone.",false);return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;}
        if(sr!=null){try{sr.cancel();}catch(Exception ignored){}try{sr.destroy();}catch(Exception ignored){}}
        sr=SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle p){status.setText("LISTENING");} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){status.setText("THINKING");} public void onError(int e){status.setText("IDLE — recognition error "+e);}
            public void onResults(Bundle r){ArrayList<String> l=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(l!=null&&!l.isEmpty())process(l.get(0));else status.setText("IDLE");}
            public void onPartialResults(Bundle p){} public void onEvent(int t,Bundle p){}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault());i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);sr.startListening(i);
    }

    private void process(String text){
        heard.setText("You: "+text);AtlasStore.addTurn(this,"user",text);
        String low=text.toLowerCase(Locale.US).trim();
        if(pendingAction!=null){
            if(low.equals("yes")||low.equals("confirm")||low.equals("do it")||low.equals("yes do it")){JSONObject a=pendingAction;pendingAction=null;speakReply(ActionEngine.execute(this,a),conversationMode);return;}
            if(low.equals("no")||low.equals("cancel")||low.equals("never mind")){pendingAction=null;speakReply("Cancelled.",conversationMode);return;}
        }
        if(low.startsWith("remember ")){String m=text.substring(9).trim();AtlasStore.remember(this,m);speakReply("Got it. I'll remember "+m,conversationMode);return;}
        if(low.equals("what do you remember")||low.equals("what do you remember?")){speakReply(memorySummary(),conversationMode);return;}
        if(low.equals("what did i miss")||low.equals("what did i miss?")){speakReply(notificationSummary(),conversationMode);return;}
        if(low.contains("battery")){speakReply(DeviceContext.batterySummary(this),conversationMode);return;}
        if(low.equals("where am i")||low.equals("where am i?")){routeAction(action("get_location",null,null),null);return;}
        if(low.startsWith("open ")){routeAction(action("open_app","app",text.substring(5).trim()),null);return;}
        if(low.startsWith("call ")){routeAction(action("call_contact","target",text.substring(5).trim()),null);return;}
        if(low.startsWith("navigate to ")){routeAction(action("navigate","query",text.substring(12).trim()),null);return;}
        if(low.startsWith("take me to ")){routeAction(action("navigate","query",text.substring(11).trim()),null);return;}
        if(low.equals("pause music")||low.equals("pause")){routeAction(action("media_pause",null,null),null);return;}
        if(low.equals("play music")||low.equals("resume music")||low.equals("play")){routeAction(action("media_play",null,null),null);return;}
        if(low.equals("next song")||low.equals("skip song")||low.equals("next")){routeAction(action("media_next",null,null),null);return;}
        if(low.equals("previous song")||low.equals("previous")){routeAction(action("media_previous",null,null),null);return;}
        if(low.contains("calendar")&&(low.contains("what")||low.contains("next")||low.contains("schedule"))){speakReply(CalendarTools.summary(this),conversationMode);return;}
        if(low.equals("what can you do")||low.equals("what can you access")||low.equals("capabilities")){speakReply(CapabilityManager.summary(this),conversationMode);return;}
        String base=AtlasStore.coreUrl(this);if(base.isEmpty()){speakReply("The phone-agent layer is working. I can open apps, navigate, use contacts for confirmed calls, control media, read your calendar, use location, and use local memory. Add Atlas Core for general intelligence.",conversationMode);return;}
        status.setText("THINKING");new Thread(()->callCore(base,text)).start();
    }

    private JSONObject action(String type,String key,String value){JSONObject a=new JSONObject();try{a.put("type",type);if(key!=null)a.put(key,value);}catch(Exception ignored){}return a;}

    private void routeAction(JSONObject a,String coreReply){
        String type=a.optString("type","");ActionEngine.Policy p=ActionEngine.policy(type);
        if(p==ActionEngine.Policy.DENY){speakReply((coreReply==null?"":coreReply+" ")+"That action is blocked or unsupported.",conversationMode);return;}
        if(p==ActionEngine.Policy.ASK){String c=ActionEngine.confirmation(this,a);if(c.startsWith("ERROR:")){speakReply(c.substring(6).trim(),conversationMode);return;}pendingAction=a;speakReply((coreReply==null||coreReply.isEmpty()?"":coreReply+" ")+c,false);return;}
        String result=ActionEngine.execute(this,a);speakReply((coreReply==null||coreReply.isEmpty()?"":coreReply+" ")+result,conversationMode);
    }

    private void callCore(String base,String text){
        try{
            String endpoint=base.endsWith("/")?base+"api/chat":base+"/api/chat";
            JSONObject body=new JSONObject();body.put("text",text);body.put("device","Titan 2");body.put("source","voice");body.put("conversationId",conversationId);
            JSONObject ctx=new JSONObject();ctx.put("recentNotifications",AtlasStore.notifications(this));ctx.put("localMemories",AtlasStore.memories(this));ctx.put("recentConversation",AtlasStore.conversation(this));ctx.put("device",DeviceContext.snapshot(this));ctx.put("capabilities",CapabilityManager.snapshot(this));ctx.put("upcomingCalendar",CalendarTools.upcoming(this));body.put("context",ctx);
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(30000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
            try(OutputStream os=c.getOutputStream()){os.write(bytes);}int code=c.getResponseCode();BufferedReader br=new BufferedReader(new InputStreamReader(code>=200&&code<300?c.getInputStream():c.getErrorStream()));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);
            if(code<200||code>=300)throw new Exception("Core HTTP "+code+" "+sb);JSONObject out=new JSONObject(sb.toString());String r=out.optString("reply","Atlas Core returned no reply.");JSONObject a=out.optJSONObject("action");runOnUiThread(()->{if(a!=null)routeAction(a,r);else speakReply(r,conversationMode);});
        }catch(Exception e){runOnUiThread(()->speakReply("Core error: "+e.getMessage(),conversationMode));}
    }

    private String memorySummary(){JSONArray a=AtlasStore.memories(this);if(a.length()==0)return "I don't have any saved memories yet.";StringBuilder s=new StringBuilder("Latest memories. ");int st=Math.max(0,a.length()-6);for(int i=st;i<a.length();i++){if(i>st)s.append(". ");s.append(a.optString(i));}return s.toString();}
    private String notificationSummary(){JSONArray a=AtlasStore.notifications(this);if(a.length()==0)return "I don't have recent notifications stored yet.";StringBuilder s=new StringBuilder("Latest notifications. ");int st=Math.max(0,a.length()-6);for(int i=st;i<a.length();i++){if(i>st)s.append(". ");s.append(a.optString(i));}return s.toString();}

    private void speakReply(String s,boolean cont){AtlasStore.addTurn(this,"assistant",s);reply.setText("Atlas: "+s);status.setText("SPEAKING");say(s,cont);}
    private void say(String s,boolean cont){if(tts==null)return;resumeAfterSpeech=cont;tts.speak(s,TextToSpeech.QUEUE_FLUSH,new Bundle(),"atlas-"+System.currentTimeMillis());}
    private void stopSpeech(){resumeAfterSpeech=false;if(tts!=null)try{tts.stop();}catch(Exception ignored){}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private void configureVoices(){
        if(tts==null)return;voiceChoices.clear();Set<Voice> all=tts.getVoices();if(all!=null)for(Voice v:all){Locale l=v.getLocale();if(l!=null&&"en".equalsIgnoreCase(l.getLanguage()))voiceChoices.add(v);}Collections.sort(voiceChoices,new Comparator<Voice>(){public int compare(Voice a,Voice b){int q=Integer.compare(b.getQuality(),a.getQuality());if(q!=0)return q;int ua="US".equalsIgnoreCase(a.getLocale().getCountry())?1:0,ub="US".equalsIgnoreCase(b.getLocale().getCountry())?1:0;if(ua!=ub)return Integer.compare(ub,ua);return a.getName().compareTo(b.getName());}});
        String saved=AtlasStore.voiceName(this);voiceIndex=-1;for(int i=0;i<voiceChoices.size();i++)if(voiceChoices.get(i).getName().equals(saved)){voiceIndex=i;break;}if(voiceIndex<0&&!voiceChoices.isEmpty())voiceIndex=0;applyVoice();
    }
    private void applyVoice(){if(tts==null)return;if(voiceIndex>=0&&voiceIndex<voiceChoices.size()){Voice v=voiceChoices.get(voiceIndex);if(tts.setVoice(v)==TextToSpeech.SUCCESS)AtlasStore.setVoiceName(this,v.getName());}tts.setSpeechRate(AtlasStore.speechRate(this));tts.setPitch(AtlasStore.pitch(this));updateVoiceInfo();}
    private void nextVoice(){if(voiceChoices.isEmpty()){toast("No alternate English voices found");return;}voiceIndex=(voiceIndex+1)%voiceChoices.size();applyVoice();say("This is Atlas voice option "+(voiceIndex+1)+".",false);}
    private void adjustRate(float d){float r=Math.max(0.65f,Math.min(1.25f,AtlasStore.speechRate(this)+d));AtlasStore.setSpeechRate(this,r);applyVoice();say("This is the new speaking speed.",false);}
    private void updateVoiceInfo(){if(voiceInfo==null)return;String n="system default";if(voiceIndex>=0&&voiceIndex<voiceChoices.size())n=voiceChoices.get(voiceIndex).getName();voiceInfo.setText("VOICE: "+n+" | speed "+String.format(Locale.US,"%.2f",AtlasStore.speechRate(this)));}

    @Override public void onInit(int x){if(x!=TextToSpeech.SUCCESS){status.setText("VOICE ERROR");return;}tts.setLanguage(Locale.US);tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){boolean go=resumeAfterSpeech;resumeAfterSpeech=false;runOnUiThread(()->{if(destroyed)return;if(go&&conversationMode){status.setText("LISTENING NEXT...");handler.postDelayed(MainActivity.this::listen,400);}else status.setText("IDLE — v5 ready");});}public void onError(String id){runOnUiThread(()->status.setText("IDLE — voice error"));}});configureVoices();status.setText("IDLE — v5 ready");}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);refreshAccess();if(r==MIC&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)listen();}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(sr!=null){try{sr.cancel();}catch(Exception ignored){}try{sr.destroy();}catch(Exception ignored){}}if(tts!=null){try{tts.stop();}catch(Exception ignored){}try{tts.shutdown();}catch(Exception ignored){}}super.onDestroy();}
}
