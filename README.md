# ExpresSense
This project is the implementation of the paper "ExpresSense: Exploring a Standalone Smartphone to Sense Engagement of Users from Facial Expressions Using Acoustic Sensing", which has been accepted to CHI 2023.
This paper aims at understing different facial expressions of users by using near-ultrasound signals (between 16-19kHz) on a commodity smartphone. Using ExpresSense, a user can play differnet YouTube videos, during which, their facial expressions are detected ubiquitously. 
By correlating the detected facial expressions with the current video genre, the engagement level of the user is produced by the application. 
For more details, please download the paper from this link: https://arxiv.org/abs/2301.06762

# Video
Teaser Video of or paper is available on YouTube : https://www.youtube.com/watch?v=p5IqMn4Q7FM

# Contributers
Pragma Kar, Shyamvanshikumar Singh, Avijit Mandal, Samiran Chattopadhyay, Sandip Chakraborty

# Project Details
## MainActivity.java 
Contains functions for generating chirps, playing chirps, recording signals, setting up the YouTube player, etc.
## SignalProcessor.java
Contains functions for different signal processing stages like Fourier Transform, Cross Correlation, Frequency bin selection, feature generation (phase, amplitude), prediction of expressions,  and related functions.
##. Result.java
Contains functions for generating graphs and engagement scores.
## Other related files : CircularBuffer.java, Filter.java
## .csv Files
Contains partial data collected from different sessions and users. 

# Reference
Please cite our paper as follows:
Kar, P., Singh, S., Mandal, A., Chattopadhyay, S., & Chakraborty, S. (2023). ExpresSense: Exploring a Standalone Smartphone to Sense Engagement of Users from Facial Expressions Using Acoustic Sensing. arXiv preprint arXiv:2301.06762.



