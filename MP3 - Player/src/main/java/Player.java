import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class Player {

    private Bitstream bitstream;
    private Decoder decoder;
    private AudioDevice device;
    private int currentFrame = 0;
    private final ReentrantLock thread = new ReentrantLock();
    private PlayerWindow window;
    private Song currentPlayingSong;
    private String[][] musicQueue;
    private ArrayList<Song> songs = new ArrayList<>();
    private String[][] unshuffledMusicQueue;
    private ArrayList<Song> unshuffledSongs = new ArrayList<>();
    private boolean shuffleActivated = false;
    private boolean loopActivated = false;
    private final Random random = new Random();
    private boolean newPlay;
    private boolean dragged = false;
    Thread threadPlaying;
    private boolean playerEnabled = false;
    private int playPauseButton = 0;
    private static final String WINDOW_TITLE = "Apoo Player";

    private final ActionListener buttonListenerPlayNow = e -> playNow();
    private final ActionListener buttonListenerRemove = e -> remove();
    private final ActionListener buttonListenerAddSong = e -> add();
    private final ActionListener buttonListenerPlayPause = e -> playPause();
    private final ActionListener buttonListenerStop = e -> stop();
    private final ActionListener buttonListenerNext = e -> next();
    private final ActionListener buttonListenerPrevious = e -> previous();
    private final ActionListener buttonListenerShuffle = e -> shuffle();
    private final ActionListener buttonListenerLoop = e -> loop();
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {scrubberRelease();}

        @Override
        public void mousePressed(MouseEvent e) {scrubberPress();}

        @Override
        public void mouseDragged(MouseEvent e) {scrubberDrag();}
    };

    public Player() {
        EventQueue.invokeLater(() -> window = new PlayerWindow(
                WINDOW_TITLE,
                this.musicQueue,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)
        );
    }

    private void playNow(){
        try {
            if (threadPlaying != null && threadPlaying.isAlive()) threadPlaying.interrupt();
            String selectedSong = window.getSelectedSong();
            int currentSongIdx = 0;
            newPlay = true;

            for (int i = 0; i < musicQueue.length; i++){ // atualiza o mini player com as informacoes da musica
                if (selectedSong == musicQueue[i][5]){
                    window.setPlayingSongInfo(musicQueue[i][0], musicQueue[i][1], musicQueue[i][2]);
                    currentSongIdx = i;
                }
            }

            currentPlayingSong = songs.get(currentSongIdx);
            playerEnabled = true;
            playPauseButton = 1;
            window.setPlayPauseButtonIcon(playPauseButton);
            window.setEnabledPlayPauseButton(playerEnabled);
            window.setEnabledStopButton(playerEnabled);
            window.setEnabledScrubber(playerEnabled);
            this.device = FactoryRegistry.systemRegistry().createAudioDevice();
            this.device.open(this.decoder = new Decoder());
            this.bitstream = new Bitstream(currentPlayingSong.getBufferedInputStream());

            skipToFrame(0);
            newPlay = false;
            currentFrame = 0;
            playing(currentPlayingSong);

        }
        catch (JavaLayerException | FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void playing(Song currentSong){
        threadPlaying = new Thread(new Runnable() {
            @Override
            public void run() {
                int musicLength = currentSong.getNumFrames();
                float musicMS = currentSong.getMsPerFrame();
                musicLength *= (int) (musicMS); //  converte os frame para milisegundos

                while (playPauseButton == 1){ // enquanto o player nao esta pausado
                    thread.lock();
                    try {
                        if (!dragged){
                            currentFrame += 1;
                            window.setTime((int) (currentFrame * musicMS), musicLength);
                        }
                        verifyNextPrevious();

                        if (!playNextFrame()){ // se não houver mais frames para reproduzir, a próxima música será reproduzida.
                            if (!loopActivated) {
                                if (currentSong == songs.get(songs.size() - 1)) {
                                    stop();
                                } else {
                                    next(); // vai para a proxima musica da fila
                                }
                            }
                            else { // se o loop estiver ativado
                                next();
                            }
                        }

                        if (newPlay){ // se uma nova música estiver prestes a tocar, interrompe a atual.
                            break;
                        }

                    } catch (JavaLayerException e) {
                        throw new RuntimeException(e);
                    }
                    finally {
                        thread.unlock();
                    }
                }
            }
        });
        threadPlaying.start();
    }

    private void remove(){
        String selectedSong = window.getSelectedSong();
        int queueLength = musicQueue.length; // tamanho da fila
        String[][] tempQueue = new String[queueLength - 1][6]; // cria uma fila com tamanho -1 do musicQueue.
        String[][] tempUnshuffledQueue = new String[queueLength - 1][6];
        int newIndex = 0; // define o novo indice da musica na fila
        int newUnshuffledIndex = 0; //  define o novo indice da musica na fila embaralhada

        for (int i = 0; i < queueLength; i++) {
            if (selectedSong != musicQueue[i][5]) { // remove o som da fila
                tempQueue[newIndex] = musicQueue[i];
                newIndex++;
            }
            else{
                if (currentPlayingSong == songs.get(i) || loopActivated){
                    next(); // se a musica nao for a ultima, pula pra proxima
                }
                if (currentPlayingSong == songs.get(songs.size()-1)){
                    stop(); //
                }
                songs.remove(i);
            }
            if (shuffleActivated) { // remove a musica da fila nao embaralhada
                if (selectedSong != unshuffledMusicQueue[i][5]) {
                    tempUnshuffledQueue[newUnshuffledIndex] = unshuffledMusicQueue[i];
                    newUnshuffledIndex++;
                } else {
                    unshuffledSongs.remove(i);
                }
            }
        }

        musicQueue = tempQueue;
        unshuffledMusicQueue = tempUnshuffledQueue;
        verifyShuffleLoop();
        window.setQueueList(musicQueue); // atualiza a playlist
    }


    //usado para adicionar as musicas a playlsit
    private void add() {
        try {
            Song song = window.openFileChooser(); // uasdo para obter a musica adicionada atualmente

            if (song != null) {
                String[] currentSong = song.getDisplayInfo(); // pega as info da musica
                int queueLength = musicQueue != null ? musicQueue.length : 0; // pega o tamanho da fila
                String[][] tempQueue = new String[queueLength + 1][6]; // cria uma fila cm comprimento +1 musicQueue.
                String[][] tempUnshuffledQueue = new String[queueLength + 1][6];

                for (int i = 0; i < queueLength; i++) { // transfere todos os elementos da fila de musica para a fila temporaria
                    tempQueue[i] = musicQueue[i];
                    if (shuffleActivated){
                        tempUnshuffledQueue[i] = unshuffledMusicQueue[i];
                    }
                }

                songs.add(song); // adiciona uma nova musica a fila
                tempQueue[queueLength] = currentSong;
                musicQueue = tempQueue;
                if (shuffleActivated){ // adciona uma nova musica a fila nao embaralhada
                    unshuffledSongs.add(song);
                    tempUnshuffledQueue[queueLength] = currentSong;
                    unshuffledMusicQueue = tempUnshuffledQueue;
                }
                verifyShuffleLoop();
                window.setQueueList(musicQueue); // atualiza a playlsit
            }
        }
        catch (IOException | BitstreamException | UnsupportedTagException | InvalidDataException e) {
            throw new RuntimeException(e);
        }
    }

    private void playPause(){
        if (playPauseButton == 1){ // pausa a musica
            playPauseButton = 0;
            window.setPlayPauseButtonIcon(playPauseButton);
        }
        else{
            playPauseButton = 1;
            window.setPlayPauseButtonIcon(playPauseButton);
            playing(currentPlayingSong);
        }

    }

    private void stop(){
        playerEnabled = false;
        playPauseButton = 0;
        window.resetMiniPlayer();
    }

    private void previous() {
        try {
            if (currentPlayingSong != songs.get(0) || loopActivated) {
                if (threadPlaying != null && threadPlaying.isAlive())
                    threadPlaying.interrupt(); // interrompe a thread de reprodução
                newPlay = true;
                int previousSongIndex = songs.size() - 1; // indice da musica anterior na fila

                if (currentPlayingSong != songs.get(0) || !loopActivated) {
                    for (int i = 0; i < songs.size(); i++) {
                        if (currentPlayingSong == songs.get(i)) { // atualiza o mini pplayer e obtem o indice da musica
                            window.setPlayingSongInfo(musicQueue[i - 1][0], musicQueue[i - 1][1], musicQueue[i - 1][2]);
                            previousSongIndex = i - 1;
                        }
                    }
                }
                else {
                    window.setPlayingSongInfo(musicQueue[previousSongIndex][0], musicQueue[previousSongIndex][1], musicQueue[previousSongIndex][2]);
                }
                currentPlayingSong = songs.get(previousSongIndex); // define a musica atual
                this.device = FactoryRegistry.systemRegistry().createAudioDevice();
                this.device.open(this.decoder = new Decoder());
                this.bitstream = new Bitstream(currentPlayingSong.getBufferedInputStream());

                skipToFrame(0);
                newPlay = false;
                currentFrame = 0;
                if (playPauseButton == 0){ // se o player estiver pausado, pula pra proxima musica
                    playPause();
                }
                else {
                    playing(currentPlayingSong);
                }
            }
        }
        catch (JavaLayerException | FileNotFoundException e){
            throw new RuntimeException(e);
        }
    }


     //pula pra proxima musica na fila
    private void next() {
        try {
            if (currentPlayingSong != songs.get(songs.size() - 1) || loopActivated) {
                if (threadPlaying != null && threadPlaying.isAlive())
                    threadPlaying.interrupt(); // interrompe a thread de reprodução
                newPlay = true;
                int nextSongIndex = 0; // indice da proxima musica da lista

                if (currentPlayingSong != songs.get(songs.size() - 1) || !loopActivated) {
                    for (int i = 0; i < songs.size(); i++) {
                        if (currentPlayingSong == songs.get(i)) { // atualiza o mini player e pega o indice da musica
                            window.setPlayingSongInfo(musicQueue[i + 1][0], musicQueue[i + 1][1], musicQueue[i + 1][2]);
                            nextSongIndex = i + 1;
                        }
                    }
                }
                else {
                    window.setPlayingSongInfo(musicQueue[0][0], musicQueue[0][1], musicQueue[0][2]);
                }
                currentPlayingSong = songs.get(nextSongIndex);
                this.device = FactoryRegistry.systemRegistry().createAudioDevice();
                this.device.open(this.decoder = new Decoder());
                this.bitstream = new Bitstream(currentPlayingSong.getBufferedInputStream());

                skipToFrame(0);
                newPlay = false;
                currentFrame = 0;
                if (playPauseButton == 0) { // se o player estiver em pausa. pula pra musica e retorna
                    playPause();
                } else {
                    playing(currentPlayingSong);
                }
            }
        }
        catch (JavaLayerException | FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Usado para ativar a reprodução aleatória na fila.
     */
    private void shuffle() {
        if (!shuffleActivated) { // se o shuffle nao estiver ativado
            unshuffledMusicQueue = Arrays.copyOf(musicQueue, musicQueue.length);
            unshuffledSongs = new ArrayList<>(songs);
            int indexCurrentPlaying = 0;

            Song[] songsArray = new Song[songs.size()];
            for (int i = 0; i < songs.size(); i++) {
                songsArray[i] = songs.get(i);
            }

            for (int i = 0; i < songs.size(); i++) {
                int index = random.nextInt(i + 1); // gera um indice aleatorio

                Song temp1 = songsArray[i];
                songsArray[i] = songsArray[index];
                songsArray[index] = temp1;

                String[] temp2 = musicQueue[i];
                musicQueue[i] = musicQueue[index];
                musicQueue[index] = temp2;
            }

            if (threadPlaying != null && threadPlaying.isAlive()){ // se a reprodução do thread estiver ativo, a musica atual vai para o inicio da fila
                for (int i = 0; i < songs.size(); i++){ // pega o indice da musica atual na fila embaralhada
                    if (songsArray[i] == currentPlayingSong){
                        indexCurrentPlaying = i;
                    }
                }
                Song temp1 = songsArray[0];
                songsArray[0] = songsArray[indexCurrentPlaying];
                songsArray[indexCurrentPlaying] = temp1;

                String[] temp2 = musicQueue[0];
                musicQueue[0] = musicQueue[indexCurrentPlaying];
                musicQueue[indexCurrentPlaying] = temp2;
            }

            songs.clear();
            Collections.addAll(songs, songsArray);
            window.setQueueList(musicQueue);
            shuffleActivated = true;
        }
        else { // se o shuffle ja estiver ativado
            musicQueue = Arrays.copyOf(unshuffledMusicQueue, unshuffledMusicQueue.length);
            songs = new ArrayList<>(unshuffledSongs);

            window.setQueueList(musicQueue);
            shuffleActivated = false;
        }
    }
    private void loop(){
        loopActivated = !loopActivated;
    }

    private void verifyShuffleLoop(){
        window.setEnabledShuffleButton(songs.size() > 1);
        window.setEnabledLoopButton(songs.size() > 0);
    }


    private void verifyNextPrevious(){
        if (loopActivated){
            window.setEnabledPreviousButton(true);
            window.setEnabledNextButton(true);
        }
        else {

            if (currentPlayingSong == songs.get(0) && currentPlayingSong == songs.get(songs.size() - 1)) {
                window.setEnabledPreviousButton(false);
                window.setEnabledNextButton(false);
            }

            if (currentPlayingSong == songs.get(0) && currentPlayingSong != songs.get(songs.size() - 1)) {
                window.setEnabledPreviousButton(false);
                window.setEnabledNextButton(true);
            }

            if (currentPlayingSong != songs.get(0) && currentPlayingSong == songs.get(songs.size() - 1)) {
                window.setEnabledPreviousButton(true);
                window.setEnabledNextButton(false);
            }

            if (currentPlayingSong != songs.get(0) && currentPlayingSong != songs.get(songs.size() - 1)) {
                window.setEnabledPreviousButton(true);
                window.setEnabledNextButton(true);
            }
        }
    }

    private void scrubberDrag(){
        dragged = true;
        currentFrame = window.getScrubberValue();
        window.setTime(currentFrame, (int) currentPlayingSong.getMsLength()); // atualiza o mini player
    }

    private void scrubberRelease(){
        try {
            if (threadPlaying != null && threadPlaying.isAlive()) threadPlaying.interrupt();
            newPlay = true;

            device = FactoryRegistry.systemRegistry().createAudioDevice();
            device.open(decoder = new Decoder());
            bitstream = new Bitstream(currentPlayingSong.getBufferedInputStream());

            int scrubberTime = window.getScrubberValue();
            float musicMS = currentPlayingSong.getMsPerFrame();
            int newFrame = (int) (scrubberTime/musicMS);

            currentFrame = 0;
            skipToFrame(newFrame); // pula o som pro proximo frame
            currentFrame = newFrame;
            dragged = false;
            newPlay = false;
            playing(currentPlayingSong); // retorna o som ao novo frame

        } catch (FileNotFoundException | JavaLayerException e) {
            throw new RuntimeException(e);
        }
    }

    private void scrubberPress(){
        dragged = true;
        currentFrame = window.getScrubberValue();
        window.setTime(currentFrame, (int) currentPlayingSong.getMsLength()); // atualiza o mini player
    }

    private boolean playNextFrame() throws JavaLayerException {
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
        return true;
    }

    // pula pro proximo frame
    private boolean skipNextFrame() throws BitstreamException {
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }
    private void skipToFrame(int newFrame) throws BitstreamException {
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
}
