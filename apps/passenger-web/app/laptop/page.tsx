import { PassengerHeader } from "../../components/passenger-header";
import { TrainSearchExperience } from "../../components/train-search-experience";

export const metadata = {
  title: "Search trains · Laptop",
};

export default function PassengerLaptopPage() {
  return (
    <div className="page-shell">
      <PassengerHeader experience="laptop" />
      <main className="passenger-laptop" id="main-content">
        <TrainSearchExperience device="laptop" />
      </main>
      <footer className="site-footer laptop-footer">
        <span>RailConnect — independent passenger timetable search</span>
      </footer>
    </div>
  );
}
