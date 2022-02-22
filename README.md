# Opioid-Overdose-Monitor
An opioid overdose monitor designed to pair with an Arduino monitoring setup with intuitive UI and emergency contact notification capability.

The Opioid Overdose Monitor companion application is designed to pair to the proprietary Mask Monitor breath and heart rate measurement device.  

The application main screen allows the user to: 	
1. Start/stop the monitoring device 	
2. Trigger/cancel an emergency 	
3. Navigate to the application settings  

The application settings screen allows the user to: 	
1. Customize emergency contacts 	
2. Customize emergency actions 	
3. Customize the emergency alarm 

Upon an emergency being triggered, either via the monitor or manual emergency slider, the application will perform the customizable emergency actions after a 5 second countdown. During the countdown alarm, the user can cancel the emergency using the same slider. If the call option is selected, the application will call the primary emergency contact. If the SMS option is selected, the application will send an SMS message to both emergency contacts containing the user's current vital readings and location.
