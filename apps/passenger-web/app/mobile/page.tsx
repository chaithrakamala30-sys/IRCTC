import { PassengerHeader } from "../../components/passenger-header";
import { TrainSearchExperience } from "../../components/train-search-experience";

export const metadata = {
  title: "Search trains · Mobile",
};

export default function PassengerMobilePage() {
  return (
    <div className="page-shell">
      <PassengerHeader experience="mobile" />
      <main className="passenger-mobile" id="main-content">
        <TrainSearchExperience device="mobile" />
      </main>

      <footer className="site-footer">
        RailConnect is independent and is not an official railway service or data feed.
      </footer>
    </div>
  );
}
