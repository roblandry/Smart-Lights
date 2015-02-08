/**
 *  Smart Lights
 *
 *  Version : 1.1-hue
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
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
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
	lights.off()
	state.previous = [:]
	state.new = [:]
	lights.each {
		state.previous[it.id] = [
			"switch": it.currentValue("switch"),
			"level" : it.currentValue("level"),
			"switchCommand": false,
			"motionCommand": false
		]
	}
	state.new = state.previous
	//log.debug "initialize: State: ${state}"
    	//log.debug "Motion Enabled: $motionEnabled"
}

def motionHandler(evt) {
    log.debug "Motion Enabled: $motionEnabled"

	//log.debug "Motion Handler - ${evt.name}: ${evt.value}, State: ${state}"
	if (evt.value == "active") {
		log.debug "Motion Detected."
		if (motionEnabled) {
			state.new = [:]
			lights.each {
				if (state.previous[it.id].switch != "on") {
					state.new[it.id] = [
						"switch": "on",
						"level" : 100,
						"switchCommand": false,
						"motionCommand": true
					]
				}
			}
			lights.on()
			//log.debug "motionHandler: State: ${state}"

		} else {
			//log.debug "motionHandler: State: ${state}"
		}
	} else if (evt.value == "inactive") {
		log.debug "Motion Ceased."
		if (motionEnabled) {
			state.motionStopTime = now()
			if(delayMinutes) {
				// This should replace any existing off schedule
				unschedule("turnOffMotionAfterDelay")
				runIn(delayMinutes*60, "turnOffMotionAfterDelay", [overwrite: false])
			} else {
				turnOffMotionAfterDelay()
			}
			//log.debug "motionHandler: State: ${state}"
		}
	}
}

def switchHandler(evt) {
	//log.debug "Switch Handler - ${evt.name}: ${evt.value}"
	if (delayMinutes) { unschedule ("turnOffMotionAfterDelay") }

	lights.each {
		if (evt.value == "off") {
			if (state.new[it.id].motionCommand) {
				log.debug "Turning ${it} off using MOTION."
			} else {
				log.debug "Turning ${it} off using SWITCH."
			}
			state.new[it.id] = [
				"switch": "off",
				"level" : 100,
				"switchCommand": false,
				"motionCommand": false
			]

		} else if (evt.value == "on") {
			if (state.new[it.id].motionCommand) {
				log.debug "Turning ${it} on using MOTION."
				state.new[it.id] = [
					"switch": "on",
					"level" : 100,
					"switchCommand": false,
					"motionCommand": true
				]
			} else {
				log.debug "Turning ${it} on using SWITCH."
				state.new[it.id] = [
					"switch": "on",
					"level" : 100,
					"switchCommand": true,
					"motionCommand": false
				]
			}
		}
	}
}

def turnOffMotionAfterDelay() {
	//log.debug "turnOffMotionAfterDelay: $state"

	if (state.motionStopTime && motionEnabled) {
		def elapsed = now() - state.motionStopTime
		if (elapsed >= (delayMinutes ?: 0) * 60000L) {
			state.new = [:]
			lights.each {
				if (state.previous[it.id].switch != "on") {
					state.new[it.id] = [
						"switch": "off",
						"level" : 100,
						"switchCommand": false,
						"motionCommand": true
					]
				}
			}
			lights.off()
			//log.debug "turnOffMotionAfterDelay: State: ${state}"
		}
	}
}
