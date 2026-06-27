# Web POS ↔ Android POS Integration Prototype

## Project Overview
This prototype demonstrates a complete integration between a Web-based POS system  and a Android POS Application.

The solution allows seamless transmission of the Grand Total from the web POS to the Android device for payment processing, with bidirectional communication (Web → Android + Android → Web confirmation).

**Live Demo Link:** http://demopos.primxtech.com/  
**Login Credentials:**  
Email: info@demo.com  
Password: 123456789

## Features Implemented

**Web ERP POS (Sender)**
- Professional product selection interface similar to retail shop POS
- Dynamic cart with quantity management
- Real-time Grand Total calculation
- “Pay (Via Android Device)” button
- Sends total amount via Bluetooth/USB
- Receives “PAID” confirmation from Android app
- Automatically completes order upon confirmation

**Android POS App (Receiver)**
- Listens for incoming data via Bluetooth and USB
- Automatically wakes screen and shows notification
- Displays received total prominently
- “CONFIRM PAYMENT” button
- Sends “PAID” signal back to Web POS
- Background listening support

## Core Requirements Fulfilled
- Connectivity: Implemented data transmission using both Bluetooth and USB cable communication.
- Web POS: Simple yet professional web interface where items can be selected.
- Android POS App: Demo application that listens for incoming payload, parses data, and displays total.
- Additional Feature: Reverse confirmation (Android sends payment confirmation back to Web POS).

## Workflow

**Web POS Sender Workflow:**
1. Select items and add to cart
2. Grand total is calculated automatically
3. Click “Pay (Via Android Device)”
4. Send total to connected Android device
5. Receive “PAID” signal from Android
6. Order is automatically completed

**Android POS Receiver Workflow:**
1. App starts and listens on Bluetooth/USB
2. Receives total amount from Web POS
3. Wakes screen and show notification
4. User confirms payment
5. Sends “PAID” signal back to Web POS
