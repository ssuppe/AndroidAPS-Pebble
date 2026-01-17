WEAR_OS_DATA_FLOW.md


# Wear OS Data Flow in AndroidAPS

This document outlines how data (Blood Glucose, IOB, COB, etc.) is gathered and transmitted from the main Android application to the Wear OS device/companion
app.

The logic is primarily contained within the **Sync Plugin**.

## Key Files

1.  **Trigger & Transport**: `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/wear/WearPlugin.kt`
2.  **Data Logic**: `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/wear/wearintegration/DataHandlerMobile.kt`

## Data Flow Step-by-Step

### 1. The Trigger
The process usually begins in `WearPlugin.kt`. The plugin subscribes to the `EventLoopUpdateGui` event via the RxBus. This event is fired by the system       
whenever a loop completes or the main GUI updates.

**File:** `WearPlugin.kt`
```kotlin                                                                                                                                                     
disposable += rxBus                                                                                                                                           
    .toObservable(EventLoopUpdateGui::class.java)                                                                                                             
    .observeOn(aapsSchedulers.io)                                                                                                                             
    .subscribe({ dataHandlerMobile.resendData("EventLoopUpdateGui") }, fabricPrivacy::logException)                                                           
                                                                                                                                                              

2. Data Gathering                                                                                                                                             

The WearPlugin delegates the actual work to DataHandlerMobile. The resendData method acts as the coordinator.                                                 

File: DataHandlerMobile.kt Method: resendData(from: String)                                                                                                   

This method systematically packages different types of data:                                                                                                  

 • Blood Glucose: Retrieves the last BG value from iobCobCalculator.ads.lastBg() and wraps it in EventData.SingleBg.                                          
 • Preferences: Sends display settings (units, max bolus, etc.).                                                                                              
 • Treatments: Calls sendTreatments() to query the database for temp basals, boluses, and predictions.                                                        
 • Status: Calls sendStatus(from) to calculate derived data.                                                                                                  

3. Calculating IOB, COB, and Loop Status                                                                                                                      

Specific calculations happen inside sendStatus within DataHandlerMobile.kt.                                                                                   

 • IOB/COB: Uses iobCobCalculator to calculate Insulin On Board and Carbs On Board.                                                                           
 • Basal: Queries processedTbrEbData for the active basal rate.                                                                                               
 • Battery: Checks receiverStatusStore (phone) and processedDeviceStatusData (pump/rig).                                                                      

These are packaged into an EventData.Status object.                                                                                                           

4. Sending the Data Object                                                                                                                                    

Once DataHandlerMobile has created a data packet (e.g., EventData.Status or EventData.SingleBg), it sends it back to the internal event bus wrapped in        
EventMobileToWear.                                                                                                                                            

File: DataHandlerMobile.kt                                                                                                                                    

                                                                                                                                                              
rxBus.send(EventMobileToWear(EventData.Status(...)))                                                                                                          
                                                                                                                                                              

5. Transmission to Wear OS                                                                                                                                    

The WearPlugin listens for these EventMobileToWear events to handle the final transmission to the OS.                                                         

File: WearPlugin.kt Method: broadcastData(payload: EventData)                                                                                                 

 1 It serializes the data payload.                                                                                                                            
 2 It constructs an Android Intent (Intents.AAPS_CLIENT_WEAR_DATA).                                                                                           
 3 It broadcasts this Intent to the specific packages associated with the Wear OS integration.                                                                

                                                                                                                                                              
private fun broadcastData(payload: EventData) {                                                                                                               
    // ... logic to determine client ID ...                                                                                                                   
    broadcast(                                                                                                                                                
        Intent(Intents.AAPS_CLIENT_WEAR_DATA)                                                                                                                 
            .putExtras(Bundle().apply {                                                                                                                       
                putString(WearDataReceiver.DATA, dataToSend.serialize())                                                                                      
            })                                                                                                                                                
    )                                                                                                                                                         
}                        