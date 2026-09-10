# Smart Home Ecosystem – Android Mobile Client

![Status](https://img.shields.io/badge/Status-Completed-brightgreen)
![Component](https://img.shields.io/badge/System-Mobile%20Client-purple)

Native Android mobile application engineered for seamless, low-latency control of the **Smart Home Ecosystem**. Gives users quick remote access to switch states, check current room conditions, and receive alerts.

## Key Features

- **Remote Control Interface:** Instant toggle switches for light channels, heating systems, and smart sockets.
- **Telemetry Display:** Real-time environmental readings (temperature, humidity, motion alerts).
- **Secure Authentication:** User login screen communicating securely with the VPS backend API.
- **Modern Android Architecture:** Clean Material UI design focusing on accessibility and fast startup times.


## Tech Stack

- **Language:** Kotlin / Java *(wybierz właściwe)*
- **Networking:** Retrofit / WebSockets / OkHttp
- **UI:** Material Design Components


## Author

- **Gabriel ([@CrimsonGabriel](https://github.com/CrimsonGabriel))** – Mobile app design, API client implementation, and UI logic.


## Related Repositories

- [Central VPS Backend](https://github.com/CrimsonGabriel/VPS-backend)
- [Web Dashboard Frontend](https://github.com/CrimsonGabriel/VPS-frontend)
- [Raspberry Pi Node](https://github.com/CrimsonGabriel/RaspberryPI)
```mermaid
graph TD
    subgraph Clients["📱 & 💻 Client Layer"]
        APP["📱 Android App<br/>(Mobile Client)"]
        WEB["💻 Web Dashboard<br/>(VPS Frontend)"]
    end

    subgraph Cloud["☁️ Cloud Infrastructure"]
        VPS["⚡ Central VPS Backend<br/>(REST API / WebSockets / DB)"]
    end

    subgraph Edge["🔌 Edge & Hardware Layer"]
        RPI["🔌 Raspberry Pi<br/>(IoT Edge Node)"]
        SENSORS["🌡️ Sensors & Actuators<br/>(Relays, Temp, Motion)"]
    end

    %% Connections
    APP <-->|"REST API / WebSockets"| VPS
    WEB <-->|"REST API / WebSockets"| VPS
    VPS <-->|"Telemetry / Commands (MQTT/REST)"| RPI
    RPI <-->|"GPIO / Serial"| SENSORS

    %% Styling
    style VPS fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style RPI fill:#1e293b,stroke:#f97316,stroke-width:2px,color:#fff
    style APP fill:#1e293b,stroke:#a855f7,stroke-width:2px,color:#fff
    style WEB fill:#1e293b,stroke:#22c55e,stroke-width:2px,color:#fff
    style SENSORS fill:#0f172a,stroke:#64748b,stroke-width:1px,color:#94a3b8
