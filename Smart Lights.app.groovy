/**
 *  Smart Lights
 *
 *  Version: 1.2-dev
 *
 *  Copyright 2015 Rob Landry
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
	name: "Smart Lights",
	namespace: "roblandry",
	author: "Rob Landry",
	description: "Turn on/off lights with motion unless overridden.",
	category: "Convenience",
	iconUrl: 	"https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance.png",
	iconX2Url: 	"https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance@2x.png")


preferences {
	section("Info") {
		paragraph "Author:  Rob Landry"
		paragraph "Version: 1.2-dev"
		paragraph "Date:    2/13/2015"
	}
	section("Devices") {
		input "motion", "capability.motionSensor", title: "Motion Sensor", multiple: false
		input "lights", "capability.switch", title: "Lights to turn on", multiple: true
	}
	section("Preferences") {
		paragraph "Motion sensor delay..."
		input "motionEnabled", "bool", title: "Enable/Disable Motion Control.", required: true, defaultValue: true
		input "delayMinutes", "number", title: "Minutes", required: false, defaultValue: 0
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(motion, "motion", motionHandler)
	subscribe(lights, "switch", switchHandler)
	state.lights = (lights.currentValue("switch")[0] == "on") ? true : false
	state.motionCommand = false
    state.motionStopTime = null
	log.debug "initialize: State: ${state}"
}

def motionHandler(evt) {
	log.debug "Motion Enabled: $motionEnabled"
	log.debug "Motion Handler - ${evt.name}: ${evt.value}, State: ${state}"

	if (!motionEnabled) {return}

	if (evt.value == "active") {
		log.info "Motion Detected."
		if (!state.lights) {
			state.motionCommand = true
			lights?.on()
		}
	} else if (evt.value == "inactive") {
		log.info "Motion Ceased."
		if (state.lights && state.motionCommand) {
			state.motionStopTime = now()
			if(delayMinutes) {
				// This should replace any existing off schedule
				unschedule("turnOffMotionAfterDelay")
				runIn(delayMinutes*60, "turnOffMotionAfterDelay", [overwrite: false])
			} else {
				turnOffMotionAfterDelay()
			}
		}
	}
}

def switchHandler(evt) {
	log.debug "Switch Handler Start: ${evt.name}: ${evt.value}, State: ${state}"

	if (delayMinutes) { 
			unschedule ("turnOffMotionAfterDelay")
			//log.debug "Unscheduled"
	}

	if (evt.value == "off") {
		log.info "Turning off."
        state.motionCommand = false
	} else if (evt.value == "on") {
		if (state.motionCommand == false) {
			log.info "Turning on using SWITCH."
		} else {
			log.info "Turning on using MOTION."
		}
	}

	state.lights = (lights.currentValue("switch")[0] == "on") ? true : false
	log.debug "Switch Handler End: ${evt.name}: ${evt.value}, State: ${state}"
}

def turnOffMotionAfterDelay() {
	log.debug "turnOffMotionAfterDelay: State: ${state}"

	if (state.motionStopTime && state.lights && motionEnabled) {
		def elapsed = now() - state.motionStopTime
		if (elapsed >= (delayMinutes ?: 0) * 60000L) {
			state.motionCommand = true
			lights.off()
		}
	}
}
