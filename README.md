# E-Commerce Conversion Rate Analysis 

**Codtech Internship | Task 3 | Data Analytics**
**Intern:** Thanyashri

## Objective
Measure the conversion rate of an online store, find where visitors drop off and why, and recommend actions to increase orders and revenue.

## Features
- Generates a simulated e-commerce dataset (60,000 sessions, year 2025) and saves it as CSV
- Data cleaning: removes duplicate sessions, fills missing values, checks funnel logic
- KPIs: conversion rate, average order value, revenue per session
- Conversion by traffic source, device, user type and country (with 95% Wilson confidence intervals)
- Monthly trend, day-of-week pattern and a source x device heatmap
- Funnel analysis: product view, add to cart, checkout, purchase, plus cart and checkout abandonment
- Statistical tests: chi-square test and a two-proportion z-test (Desktop vs Mobile)
- Opportunity sizing: extra orders and revenue if weak segments reached the average
- Auto-generated insights and a visual HTML report with charts

## Tech Stack
- Java 11 or newer (standard library only, no external jars or Maven)
- VS Code with the Extension Pack for Java
- HTML and SVG for the report charts

## Project Structure
```
ecommerce-conversion/
|-- src/
|   `-- EcommerceConversionAnalysis.java
|-- ecommerce_sessions.csv      (generated dataset)
|-- report.html                 (generated visual report)
`-- README.md
```

## How to Run
1. Install a JDK (11 or newer), for example Temurin JDK 21.
2. Open the project folder in VS Code.
3. Run from the terminal:
   ```
   java src/EcommerceConversionAnalysis.java
   ```
   Or open the Java file and click **Run** above `main`.
4. Open `report.html` in a browser to view the charts.

## Key Findings
| Insight | Result |
|---|---|
| Overall conversion rate | 3.18% from 60,000 sessions |
| Best traffic source | Email (6.19%) |
| Weakest traffic source | Social Media (1.34%) |
| Desktop vs Mobile | Desktop converts about 2.3x better |
| Returning vs new users | Returning users convert about 2.8x better |
| Peak month | December (4.61%) |
| Country effect | Not statistically significant |
| Cart abandonment | 66.9% |
| Checkout abandonment | 45.8% |
| Largest opportunity | Improving new-user conversion (about 480 extra orders) |

## Recommendations
- **Mobile checkout:** add guest checkout and UPI / wallet payments, reduce form fields, use larger buttons.
- **Social media:** use dedicated landing pages, retargeting and shoppable posts.
- **Email and returning users:** grow the email list, send cart-abandonment and win-back emails, add loyalty rewards.
- **New users:** add trust signals (reviews, return policy, secure-payment badges) and a first-order offer.
- **Seasonality:** plan stock, ad budget and server capacity before November and December.
- **Abandonment:** show shipping and tax early, add exit-intent offers, send reminder emails within one hour.

## Screenshots
Add screenshots of `report.html` here, for example:
```
![Report overview](screenshots/report-overview.png)
```

## Note
The dataset is simulated with a fixed random seed because real store data is private. The same analysis logic works on a real export from Google Analytics or Shopify.
