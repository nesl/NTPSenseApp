import os
import wave 
import sys



for filename in os.listdir("."):
    if filename.endswith(".pcm"): 
    	with open(filename, 'rb') as pcmfile:
        	pcmdata = pcmfile.read()
		
    	with wave.open(filename[:-4]+'.wav', 'wb') as wavfile:
        	wavfile.setparams((1, 2, 44100, 0, 'NONE', 'NONE'))
        	wavfile.writeframes(pcmdata)